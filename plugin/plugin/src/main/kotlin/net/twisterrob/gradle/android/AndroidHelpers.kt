package net.twisterrob.gradle.android

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.FeaturePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestPlugin
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.VersionedVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.plugins.PluginContainer

fun PluginContainer.hasAndroid(): Boolean =
	hasPlugin(AppPlugin::class.java) ||
			hasPlugin(LibraryPlugin::class.java) ||
			hasPlugin(FeaturePlugin::class.java) ||
			hasPlugin(TestPlugin::class.java)

val BaseExtension.variants: DomainObjectSet<out BaseVariant>
	get() =
		when (this) {
			is AppExtension -> applicationVariants
			is FeatureExtension -> featureVariants
			is LibraryExtension -> libraryVariants
			is TestExtension -> applicationVariants
			is TestedExtension -> testVariants
			else -> throw IllegalArgumentException("Unknown extension: $this")
		}

@Suppress("unused")
fun BaseVariant.toDebugString() = (
		"${this::class}"
				+ ", name=$name, desc=$description, base=$baseName, dir=$dirName, pkg=$applicationId, flav=$flavorName"
				+ (if (this is VersionedVariant) ", ver=$versionName, code=$versionCode" else "")
		)
