import net.twisterrob.gradle.build.dsl.base
import net.twisterrob.gradle.build.dsl.gradlePlugin
import net.twisterrob.gradle.build.dsl.java
import net.twisterrob.gradle.build.dsl.publishing
import net.twisterrob.gradle.build.publishing.GradlePluginValidationPlugin
import net.twisterrob.gradle.build.publishing.getChild
import net.twisterrob.gradle.build.publishing.withDokkaJar
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.dokka.gradle.DokkaTask
import java.time.Instant
import java.time.format.DateTimeFormatter

plugins {
	id("org.gradle.maven-publish")
	id("org.gradle.signing")
	id("org.jetbrains.dokka")
}
plugins.apply(GradlePluginValidationPlugin::class)

group = project.property("projectGroup").toString()
version = project.property("projectVersion").toString()

plugins.withId("org.gradle.java") {
	afterEvaluate {
		// Delayed configuration, so that project.* is set up properly in corresponding modules' build.gradle.kts.
		tasks.named<Jar>("jar") {
			manifest {
				attributes(
					mapOf(
						// Implementation-* used by TestPlugin
						"Implementation-Vendor" to project.group,
						"Implementation-Title" to project.base.archivesName.get(),
						"Implementation-Version" to project.version,
						"Built-Date" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
					)
				)
			}
		}
	}
}

normalization {
	runtimeClasspath {
		metaInf {
			ignoreAttribute("Built-Date")
		}
	}
}

/**
 * @see org.jetbrains.dokka.gradle.DokkaPlugin
 */
val DOKKA_TASK_NAME: String = "dokkaJavadoc"

		// Note: org.gradle.api.publish.plugins.PublishingPlugin.apply calls publications.all,
		// so most code here is eagerly executed, even inside register { }!

		project.java.withDokkaJar(project, project.tasks.named(DOKKA_TASK_NAME))
		project.java.withSourcesJar()
		setupDoc(project)
		setupSigning(project)
		project.plugins.withId("net.twisterrob.gradle.build.module.library") {
			project.publishing.apply {
				publications {
					register<MavenPublication>("library") {
						setupModuleIdentity(project)
						setupPublication(project)
						// compiled files: artifact(tasks["jar"])) { classifier = null } + dependencies
						from(project.components["java"])
					}
				}
			}
		}
		project.plugins.withId("net.twisterrob.gradle.build.module.gradle-plugin") {
			registerPublicationsTasks(project)
			@Suppress("UnstableApiUsage")
			project.gradlePlugin.apply {
				website.set("https://github.com/TWiStErRob/net.twisterrob.gradle")
				vcsUrl.set("https://github.com/TWiStErRob/net.twisterrob.gradle.git")
			}
			// Configure built-in pluginMaven publication created by java-gradle-plugin.
			// Have to do it in afterEvaluate, because it's delayed in MavenPluginPublishPlugin.
			// Cannot be relying on the `maybeCreate` usage in MavenPluginPublishPlugin.addMainPublication,
			// because the module name is set in afterEvaluate in setupModuleIdentity and MPPP already read it.
			// This is described in https://github.com/gradle/gradle/issues/23551.
			project.afterEvaluate {
				project.publishing.apply {
					publications {
						named<MavenPublication>("pluginMaven").configure pluginMaven@{
							setupModuleIdentity(project)
							setupPublication(project)
							handleTestFixtures()
							// TODEL work around https://github.com/gradle/gradle/issues/23551
							fixMarkers(project)
						}
						withType<MavenPublication>()
							.matching { it.name.endsWith("PluginMarkerMaven") }
							.configureEach pluginMarkerMaven@{
								setupPublication(project)
							}
					}
				}
			}
		}

fun MavenPublication.setupPublication(project: Project) {
	project.configure<SigningExtension> {
		sign(this@setupPublication)
	}
	setupLinks(project)
	reorderNodes(project)
}

fun MavenPublication.fixMarkers(project: Project) {
	project.gradlePlugin.plugins.forEach { plugin ->
		// Needs to be eager getByName rather than named because we're already inside a named block at call-site.
		project.publishing.publications.getByName<MavenPublication>("${plugin.name}PluginMarkerMaven") {
			pom.withXml {
				asNode()
					.getChild("dependencies")
					.getChild("dependency")
					.getChild("artifactId")
					.setValue(this@fixMarkers.artifactId)
			}
		}
	}
}

@Suppress("UnusedReceiverParameter")
fun MavenPublication.handleTestFixtures() {
	// Disable publication of test fixtures as it could leak internal dependencies.
	val java = components["java"] as AdhocComponentWithVariants
	java.withVariantsFromConfiguration(configurations.getByName("testFixturesApiElements")) { skip() }
	java.withVariantsFromConfiguration(configurations.getByName("testFixturesRuntimeElements")) { skip() }
	// Not suppressing warnings, because they should be skipped, if they show up, that's a problem.
	//suppressPomMetadataWarningsFor("testFixturesApiElements")
	//suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
}

fun setupDoc(project: Project) {
	project.tasks.named<DokkaTask>(DOKKA_TASK_NAME) {
		// TODO https://github.com/Kotlin/dokka/issues/1894
		moduleName.set(this.project.base.archivesName)
		dokkaSourceSets.configureEach {
			reportUndocumented.set(false)
		}
	}
}

fun setupSigning(project: Project) {
	project.configure<SigningExtension> {
		//val signingKeyId: String? by project // Gradle 6+ only
		// -PsigningKey to gradlew, or ORG_GRADLE_PROJECT_signingKey env var
		val signingKey: String? by project
		// -PsigningPassword to gradlew, or ORG_GRADLE_PROJECT_signingPassword env var
		val signingPassword: String? by project
		if (signingKey != null && signingPassword != null) {
			useInMemoryPgpKeys(signingKey, signingPassword)
		} else {
			setRequired { false }
		}
	}
}

fun MavenPublication.setupModuleIdentity(project: Project) {
	project.afterEvaluate {
		artifactId = project.base.archivesName.get()
		version = project.version as String

		pom {
			val projectDescription = project.description?.takeIf { it.contains(": ") && it.endsWith(".") }
				?: error(
					"${project} must have a description with format: \"Module Display Name: Module description.\""
							+ ", found ${project.description}"
				)
			name.set(projectDescription.substringBefore(": ").also { check(it.isNotBlank()) })
			description.set(projectDescription.substringAfter(": ").also { check(it.isNotBlank()) })
		}
	}
}

fun MavenPublication.setupLinks(project: Project) {
	pom {
		url.set("https://github.com/TWiStErRob/net.twisterrob.gradle")
		scm {
			connection.set("scm:git:github.com/TWiStErRob/net.twisterrob.gradle.git")
			developerConnection.set("scm:git:ssh://github.com/TWiStErRob/net.twisterrob.gradle.git")
			url.set("https://github.com/TWiStErRob/net.twisterrob.gradle/tree/main")
		}
		licenses {
			license {
				name.set("Unlicense")
				url.set("https://github.com/TWiStErRob/net.twisterrob.gradle/blob/v${project.version}/LICENCE")
			}
		}
		developers {
			developer {
				id.set("TWiStErRob")
				name.set("Robert Papp")
				email.set("papp.robert.s@gmail.com")
			}
		}
	}
}

fun MavenPublication.reorderNodes(project: Project) {
	project.afterEvaluate {
		pom.withXml {
			asNode().apply {
				val lastNodes = listOf(
					getChild("modelVersion"),
					getChild("groupId"),
					getChild("artifactId"),
					getChild("version"),
					getChild("name"),
					getChild("description"),
					getChild("url"),
					getChild("dependencies"),
					getChild("scm"),
					getChild("developers"),
					getChild("licenses")
				)
				lastNodes.forEach { remove(it) }
				lastNodes.forEach { append(it) }
			}
		}
	}
}

/**
 * Create convenience lifecycle tasks for markers.
 *
 * @see org.gradle.plugin.devel.plugins.MavenPluginPublishPlugin.createMavenMarkerPublication
 * @see org.gradle.api.publish.maven.plugins.MavenPublishPlugin.createPublishTasksForEachMavenRepo
 */
fun registerPublicationsTasks(project: Project) {
	val markersName = "allPluginMarkerMavenPublications"
	val markersDescription = "all Gradle Plugin Marker publications"
	val markerPublications = project.the<PublishingExtension>()
		.publications
		.matching {
			it is MavenPublication && it.name.endsWith("PluginMarkerMaven")
		}
	project.tasks.register("publish${markersName.capitalized()}ToMavenLocal") task@{
		group = PublishingPlugin.PUBLISH_TASK_GROUP
		description = "Publishes ${markersDescription} produced by this project to the local Maven cache."
		markerPublications.all publication@{
			val publication = this@publication.name
			this@task.dependsOn("publish${publication.capitalized()}PublicationToMavenLocal")
		}
	}
	project.the<PublishingExtension>().repositories.all repository@{
		val repository = this@repository.name
		project.tasks.register("publish${markersName.capitalized()}To${repository.capitalized()}Repository") task@{
			group = PublishingPlugin.PUBLISH_TASK_GROUP
			description = "Publishes ${markersDescription} produced by this project to the ${repository} repository."
			markerPublications.all publication@{
				val publication = this@publication.name
				this@task.dependsOn("publish${publication.capitalized()}PublicationTo${repository.capitalized()}Repository")
			}
		}
	}
}