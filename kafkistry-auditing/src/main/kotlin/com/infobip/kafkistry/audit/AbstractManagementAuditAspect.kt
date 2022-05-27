package com.infobip.kafkistry.audit

import org.aspectj.lang.ProceedingJoinPoint
import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

open class AbstractManagementAuditAspect(
        private val eventsListener: ManagementEventsListener,
        private val userResolver: CurrentRequestUserResolver
) {

    private val log = LoggerFactory.getLogger(AbstractManagementAuditAspect::class.java)

    //A -> argument
    fun <E : AuditEvent, A : Any> ProceedingJoinPoint.executeAndEmitEvent(
            event: E,
            firstArgKClass: KClass<A>,
            stateApply: E.(A) -> Unit
    ): Any? {
        return executionCapture(event)
                .argumentCaptor(firstArgKClass.java, 0, stateApply)
                .execute()
    }

    //A1 -> 1st argument
    //A2 -> 2nd argument
    fun <E : AuditEvent, A1 : Any, A2 : Any> ProceedingJoinPoint.executeAndEmitEvent(
            event: E,
            firstArgKClass: KClass<A1>,
            secondArgKClass: KClass<A2>,
            stateApply: E.(A1, A2) -> Unit
    ): Any? {
        return executionCapture(event)
                .argumentsCaptor(firstArgKClass.java, secondArgKClass.java, stateApply)
                .execute()
    }

    fun <E : AuditEvent> ProceedingJoinPoint.executionCapture(event: E)
        = CapturingExecution(event, this)

    class ArgumentCaptor<E : AuditEvent, A : Any?>(
            private val argClass: Class<A>,
            private val index: Int,
            private val argumentConsumer: E.(A) -> Unit = {}
    ) {

        private var value = AtomicReference<A>()

        fun ProceedingJoinPoint.capture() = value.set(argClass.doCast(args[index]))
        fun get(): A = value.get()
        fun apply(event: E) = event.argumentConsumer(get())
    }

    class StateCaptor<E : AuditEvent, S : Any?>(
            private val stateExtractor: () -> S,
            private val applier: E.(S, S) -> Unit
    ) {

        private var before = AtomicReference<S>()
        private var after = AtomicReference<S>()

        fun captureBefore() = before.set(stateExtractor())
        fun captureAfter() = after.set(stateExtractor())
        fun apply(event: E) = event.applier(before.get(), after.get())
    }

    class ResultCaptor<E : AuditEvent, R : Any?>(
            private val resultExtractor: (Any?) -> R,
            private val resultConsumer: E.(R) -> Unit
    ) {

        companion object {
            //val NO_OP = ResultCaptor<*, *>({  }, {  }).apply { result.set(Unit) }
            fun <E : AuditEvent> noOp() = ResultCaptor<E, Unit>({}, {}).apply { result.set(Unit) }
        }

        private var result = AtomicReference<R>()

        fun capture(result: Any?) = this.result.set(resultExtractor(result))
        fun apply(event: E) = event.resultConsumer(result.get())
    }

    inner class CapturingExecution<E : AuditEvent>(
            private val event: E,
            private val joinPoint: ProceedingJoinPoint
    ) {

        private val argumentCaptors: MutableList<ArgumentCaptor<E, *>> = mutableListOf()
        private val stateCaptors: MutableList<StateCaptor<E, *>> = mutableListOf()
        private var resultCaptor: ResultCaptor<E, *> = ResultCaptor.noOp()
        private val finishOperations: MutableList<(E) -> Unit> = mutableListOf()

        fun <A : Any?> argumentCaptor(
                argClass: Class<A>,
                argIndex: Int = argumentCaptors.size,
                stateApply: E.(A) -> Unit
        ): CapturingExecution<E> {
            argumentCaptors.add(ArgumentCaptor(
                    argClass = argClass,
                    index = argIndex,
                    argumentConsumer = stateApply
            ))
            return this
        }

        fun <A1 : Any?, A2 : Any?> argumentsCaptor(
                firstArgClass: Class<A1>,
                secondArgClass: Class<A2>,
                stateApply: E.(A1, A2) -> Unit
        ): CapturingExecution<E> {
            val argCaptor1 = ArgumentCaptor<E, A1>(firstArgClass, 0).also { argumentCaptors.add(it) }
            val argCaptor2 = ArgumentCaptor<E, A2>(secondArgClass, 1).also { argumentCaptors.add(it) }
            finishOperations.add {
                it.stateApply(argCaptor1.get(), argCaptor2.get())
            }
            return this
        }

        fun <A1 : Any?, A2 : Any?, S : Any?> stateCaptor(
                firstArgClass: Class<A1>,
                secondArgClass: Class<A2>,
                stateSupplier: (A1, A2) -> S,
                stateApply: E.(A1, A2, S, S) -> Unit
        ): CapturingExecution<E> {
            val argCaptor1 = ArgumentCaptor<E, A1>(firstArgClass, 0).also { argumentCaptors.add(it) }
            val argCaptor2 = ArgumentCaptor<E, A2>(secondArgClass, 1).also { argumentCaptors.add(it) }
            stateCaptors.add(StateCaptor(
                    stateExtractor = { stateSupplier(argCaptor1.get(), argCaptor2.get()) },
                    applier = { before, after -> stateApply(argCaptor1.get(), argCaptor2.get(), before, after) }
            ))
            return this
        }

        fun <R : Any?> resultCaptor(clazz: Class<R>, consumer: E.(R?) -> Unit): CapturingExecution<E> {
            resultCaptor = ResultCaptor(
                    resultExtractor = { clazz.cast(it) },
                    resultConsumer = { consumer(it) }
            )
            return this
        }

        fun execute(): Any? {
            return joinPoint.executeAndEmitEvent(Execution(
                    event = event,
                    argumentCaptors = argumentCaptors,
                    stateCaptors = stateCaptors,
                    resultCaptor = resultCaptor,
                    finishOperations = finishOperations
            ))
        }
    }

    class Execution<E : AuditEvent>(
            val event: E,
            val argumentCaptors: List<ArgumentCaptor<E, *>>,
            val stateCaptors: List<StateCaptor<E, *>>,
            val resultCaptor: ResultCaptor<E, *>,
            private val finishOperations: List<(E) -> Unit>
    ) {
        fun finalize() {
            argumentCaptors.forEach { it.apply(event) }
            stateCaptors.forEach { it.apply(event) }
            resultCaptor.apply(event)
            finishOperations.forEach { it(event) }
        }
    }

    fun <E : AuditEvent> ProceedingJoinPoint.executeAndEmitEvent(
            execution: Execution<E>
    ): Any? {
        execution.event.serviceClass = target.javaClass.name
        execution.event.methodName = signature.name
        execution.event.user = userResolver.resolveUserOrUnknown()
        executeAndLogIfException {
            execution.argumentCaptors.forEach { with(it) { capture() } }
            execution.stateCaptors.forEach { it.captureBefore() }
        }
        try {
            return proceed().also { //actual job execution
                executeAndLogIfException {
                    execution.resultCaptor.capture(it)
                }
            }
        } catch (ex: Throwable) {
            execution.event.encounteredException = ex.deepToString()
            throw ex
        } finally {
            executeAndLogIfException {
                execution.stateCaptors.forEach { it.captureAfter() }
            }
            val event = execution.event.apply {
                executeAndLogIfException { execution.finalize() }
            }
            executeAndLogIfException {
                eventsListener.acceptEvent(event)
                //prevent new exception suppressing processing exception
            }
        }
    }

    private fun executeAndLogIfException(operation: () -> Unit) {
        try {
            operation()
        } catch (th: Throwable) {
            log.error("Unexpected capturing execution exception, swallowing", th)
        }
    }

}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
private fun <T> Class<T>.doCast(value: Any?): T? {
    if (value == null) {
        return null
    }
    return when (this) {
        Int::class.java -> (value as Integer).toInt() as T
        Long::class.java -> (value as java.lang.Long).toLong() as T
        Double::class.java -> (value as java.lang.Double).toDouble() as T
        Boolean::class.java -> (value as java.lang.Boolean).booleanValue() as T
        Byte::class.java -> (value as java.lang.Byte).toByte() as T
        Char::class.java -> (value as Character).charValue() as T
        Float::class.java -> (value as java.lang.Float).toFloat() as T
        Short::class.java -> (value as java.lang.Short).toShort() as T
        else -> cast(value)
    }
}
