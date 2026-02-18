package dev.thymian.client.endpoints

import com.intellij.microservices.endpoints.*
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import dev.thymian.client.cli.*
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture

class ThymianEndpointsRunCheckIntegrationTest : BasePlatformTestCase() {
    private lateinit var testCli: TestThymianCLI
    private lateinit var testEndpoints: FakeApiDefinitionEndpoints

    override fun setUp() {
        super.setUp()
        testCli = TestThymianCLI()
        testEndpoints = FakeApiDefinitionEndpoints(project)

        ExtensionTestUtil.maskExtensions(
            EndpointsProvider.EP_NAME,
            listOf(testEndpoints.endpointsProvider),
            testRootDisposable
        )

        application.replaceService(
            ThymianCLISessionManager::class.java,
            object : ThymianCLISessionManager {
                override fun getThymianCLI(project: Project): CompletableFuture<ThymianCLI> {
                    return CompletableFuture.completedFuture(testCli)
                }
            },
            testRootDisposable
        )
    }

    override fun tearDown() {
        try {
            val editorFactory = EditorFactory.getInstance()
            editorFactory.allEditors.forEach { editor ->
                if (!editor.isDisposed) {
                    editorFactory.releaseEditor(editor)
                }
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test running a complete check against the CLI`() {
        // commented until fix of youtrack.jetbrains.com/issue/IDEA-385865/JSVG-version-issue-using-BasePlatformTestCase-with-Microservice-Plugin
//        val dataContext = DataContext { key ->
//            when (key) {
//                PlatformCoreDataKeys.SELECTED_ITEMS.name -> testEndpoints.items.toTypedArray()
//                CommonDataKeys.PROJECT.name -> project
//                else -> null
//            }
//        }
//
//        val action = ThymianEndpointsRunCheckAction()
//        val event = AnActionEvent.createEvent(dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
//
//        action.actionPerformed(event)
//
//        while (!testCli.initialized) {
//            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
//            Thread.sleep(100)
//        }
//
//        testCli.completionFuture.join()
//
//        val actions = testCli.completionFuture.get()
//        assertEquals(2, actions.size)
//        actions[0].let {
//            assertTrue(it is EmitActionMessage.OpenAPITransform)
//            assertEquals(testEndpoints.fileContent, (it as EmitActionMessage.OpenAPITransform).payload.content)
//        }
//        assertTrue(actions[1] is EmitActionMessage.HttpLinterLintStatic)
    }
}

typealias Group = String
typealias Endpoint = String

private class FakeApiDefinitionEndpoints(project: Project) {
    val fileContent = """
openapi: 3.0.0
info:
  title: TestAPI
  version: "1.0"
paths:
  /:
    get:
      parameters: [ ]
      responses:
        "200":
          description: ""
""".trimIndent()
    private val groups = listOf("test-group")
    private val endpoints = listOf("endpoint")
    private val file by lazy {
        PsiFileFactory.getInstance(project).createFileFromText(
            "openapi.yaml",
            com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE,
            fileContent
        )
    }

    val endpointsProvider = object : EndpointsProvider<Group, Endpoint> {
        override val presentation = FrameworkPresentation("ThymianTest", "Test Endpoints Provider", null)
        override val endpointType = API_DEFINITION_TYPE

        override fun getEndpointGroups(project: Project, filter: EndpointsFilter) =
            if (filter is ModuleEndpointsFilter) {
                emptyList()
            } else {
                groups
            }

        override fun getEndpoints(group: Group) = if (groups.contains(group)) {
            endpoints
        } else {
            emptyList()
        }

        override fun getNavigationElement(group: Group, endpoint: Endpoint): PsiFile? {
            return if (groups.contains(group) && endpoints.contains(endpoint)) {
                file
            } else {
                null
            }
        }

        override fun getStatus(project: Project) = EndpointsProvider.Status.AVAILABLE

        override fun isValidEndpoint(group: Group, endpoint: Endpoint) = true

        override fun getModificationTracker(project: Project) = ModificationTracker.NEVER_CHANGED

        override fun getEndpointPresentation(group: Group, endpoint: Endpoint): ItemPresentation {
            return object : ItemPresentation {
                override fun getPresentableText(): String {
                    return "$group:$endpoint"
                }

                override fun getIcon(p0: Boolean) = null
            }
        }
    }

    val items = groups.flatMap { g ->
        endpoints.map { e ->
            object : EndpointsElementItem<Group, Endpoint> {
                override val module = null
                override val provider = endpointsProvider
                override val group = g
                override val endpoint = e
                override val isValid = true
            }
        }
    }
}

private class TestThymianCLI : ThymianCLI {
    private val lock = Any()
    val completionFuture = CompletableFuture<List<EmitActionMessage>>()
    private val actions = mutableListOf<EmitActionMessage>()
    private var _initialized = false
    val initialized get() = synchronized(lock) { _initialized }

    override fun initialize(messageListener: (String) -> Unit): CompletableFuture<Unit> {
        synchronized(lock) {
            _initialized = true
        }
        return CompletableFuture.completedFuture(Unit)
    }

    override fun <T : Any> sendEvent(event: EmitEventMessage<T>) {
        // no-op
    }

    override fun <S : ActionResultMessage<T>, T : Any> sendAction(
        action: EmitActionMessage,
        listener: ActionListener<S, T>
    ) {
        synchronized(lock) {
            if (!_initialized) {
                val ex = IllegalStateException("CLI not initialized")
                completionFuture.completeExceptionally(ex)
                throw IllegalStateException("ex")
            }

            actions += action
        }

        when (action) {
            is EmitActionMessage.OpenAPITransform -> listener.handleResult(
                ActionResultMessage.OpenAPITransformResponse(
                    correlationId = action.id,
                    name = "openapi.transform",
                    payload = Json.decodeFromString("{}")
                )
            )

            is EmitActionMessage.HttpLinterLintStatic -> listener.handleResult(
                ActionResultMessage.HttpLinterLintStaticResponse(
                    correlationId = action.id,
                    name = "http-linter.lint-static",
                    payload = listOf(
                        ActionResultMessage.HttpLinterLintStaticResponse.Payload(
                            valid = true,
                            reports = emptyList()
                        )
                    )
                )
            )
        }
    }

    override fun close(): CompletableFuture<Unit> {
        synchronized(lock) {
            completionFuture.complete(actions)
        }
        return CompletableFuture.completedFuture(Unit)
    }
}
