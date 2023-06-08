package net.twisterrob.gradle.graph

import net.twisterrob.gradle.graph.tasks.TaskData
import net.twisterrob.gradle.graph.tasks.TaskGatherer
import net.twisterrob.gradle.graph.tasks.TaskResult
import net.twisterrob.gradle.graph.vis.TaskVisualizer
import net.twisterrob.gradle.graph.vis.d3.javafx.D3JavaFXTaskVisualizer
import net.twisterrob.gradle.graph.vis.graphstream.GraphStreamTaskVisualizer
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.TaskState
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.filelock.LockOptionsBuilder.mode
import org.gradle.cache.scopes.ScopedCacheBuilderFactory
import javax.inject.Inject

class GraphPlugin @Inject constructor(
	private val cacheRepository: ScopedCacheBuilderFactory,
) : Plugin<Settings> {

	@Suppress("LateinitUsage") // TODO refactor to object.
	private lateinit var vis: TaskVisualizer

	/** See [SO](http://stackoverflow.com/a/11237184/253468). */
	override fun apply(project: Settings) {
		val extension = project.extensions.create("graphSettings", GraphSettingsExtension::class.java)

		val gatherer = TaskGatherer(project)
		project.gradle.addBuildListener(object : BuildAdapter() {
			override fun settingsEvaluated(settings: Settings) {
				vis = createGraph(extension.visualizer)
				vis.showUI(settings)
				gatherer.isSimplify = extension.isSimplifyGraph
			}

			@Deprecated("Not compatible with configuration cache.")
			override fun buildFinished(result: BuildResult) {
				if (extension.isKeepOpen) {
					vis.closeUI()
				}
			}
		})

		gatherer.taskGraphListener = object : TaskGatherer.TaskGraphListener {
			override fun graphPopulated(graph: Map<Task, TaskData>) {
				vis.initModel(graph)
			}
		}

		@Suppress("DEPRECATION") // Configuration cache.
		project.gradle.taskGraph.addTaskExecutionListener(object : org.gradle.api.execution.TaskExecutionListener {
			override fun beforeExecute(task: Task) {
				vis.update(task, TaskResult.Executing)
			}

			override fun afterExecute(task: Task, state: TaskState) {
				vis.update(task, getResult(task, state))
			}
		})
	}

	private fun createGraph(visualizer: Class<out TaskVisualizer>?): TaskVisualizer {
		val cache = cacheRepository
			.createCacheBuilder("graphSettings")
			.withDisplayName("graph visualization settings")
			.withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
			.open()

		return newVisualizer(visualizer, cache)
	}

	companion object {

		private fun getResult(task: Task, state: TaskState): TaskResult =
			when {
				state.failure != null ->
					TaskResult.Failure

				state.skipped && state.skipMessage == "SKIPPED" ->
					TaskResult.Skipped

				state.skipped && state.skipMessage == "UP-TO-DATE" ->
					TaskResult.UpToDate

				!state.didWork ->
					TaskResult.NoWork

				state.executed && state.didWork ->
					TaskResult.Completed

				else ->
					error(
						"""
							What happened with ${task.name}? The task state is unrecognized:
								Executed: ${state.executed}
								Did work: ${state.didWork}
								Skipped: ${state.skipped}
								Skip message: ${state.skipMessage}
								Failure: ${state.failure}
						""".trimIndent()
					)
			}
	}
}

@Suppress("UnnecessaryAbstractClass") // Gradle extensions must be abstract.
abstract class GraphSettingsExtension {

	var isKeepOpen: Boolean = false

	/** A [TaskVisualizer] implementation class, null means automatic. */
	var visualizer: Class<out TaskVisualizer>? = null

	var isSimplifyGraph: Boolean = true
}

private fun hasJavaFX(): Boolean =
	try {
		javafx.application.Platform::class.java
		true
	} catch (ignore: NoClassDefFoundError) {
		val dependency = """
			No JavaFX Runtime found on buildscript classpath,
			falling back to primitive GraphStream visualization.
			You can ensure JavaFX or ask for GraphStream explicitly:
			graphSettings {
				visualizer = ${GraphStreamTaskVisualizer::class.java.name}
			}
		""".trimIndent()
		System.err.println(dependency) // TODO logging
		false
	}

private fun newVisualizer(visualizerClass: Class<out TaskVisualizer>?, cache: PersistentCache): TaskVisualizer {
	val visualizer = visualizerClass ?: if (hasJavaFX()) {
		D3JavaFXTaskVisualizer::class.java
	} else {
		GraphStreamTaskVisualizer::class.java
	}

	var err: Throwable? = null
	if (!TaskVisualizer::class.java.isAssignableFrom(visualizer)) {
		err = IllegalArgumentException("visualizer must implement ${TaskVisualizer::class.java}").fillInStackTrace()
	}

	var graph: TaskVisualizer? = null
	try {
		graph = visualizer.getConstructor(PersistentCache::class.java).newInstance(cache)
	} catch (ex: ReflectiveOperationException) {
		err = ex
	}
	if (graph == null) {
		try {
			graph = visualizer.getConstructor().newInstance()
		} catch (ex: ReflectiveOperationException) {
			err = ex
		}
	}
	if (graph == null) {
		throw IllegalArgumentException(
			"Invalid value for visualizer: ${visualizer}," +
					"make sure the class has a default or a ${PersistentCache::class.java} constructor",
			err
		)
	}
	return graph
}