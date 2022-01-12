package com.infobip.kafkistry.webapp

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import com.infobip.kafkistry.webapp.security.CurrentRequestUserResolver
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Aspect
@Component
class ExecutingWebCallsTrackerAspect(
        private val userResolver: CurrentRequestUserResolver
) {

    private val currentCalls = ConcurrentHashMap<Call, Int>()

    fun currentCallsCounts(): List<CallCount> {
        return currentCalls.asSequence()
                .filter { (_, count) -> count > 0 }
                .map { (call, count) -> CallCount(call, count) }
                .toList()
    }

    @Around("execution(* com.infobip.kafkistry.api.*.*(..))")
    fun invokeAnyApiMethod(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.forwardAndTrackCall()
    }

    @Around("execution(* com.infobip.kafkistry.webapp.controller.*.*(..))")
    fun invokeAnyWebControllerMethod(joinPoint: ProceedingJoinPoint): Any? {
        return joinPoint.forwardAndTrackCall()
    }

    private fun ProceedingJoinPoint.forwardAndTrackCall(): Any? {
        val call = Call(
                component = target.javaClass.name,
                method = signature.name,
                user = userResolver.resolveUser()?.fullName ?: "anonymous"
        )
        currentCalls.merge(call, 1) { current, inc -> current + inc }
        try {
            return proceed()
        } finally {
            currentCalls.merge(call, -1) { current, inc -> (current + inc).takeIf { it > 0 } }
        }
    }
}

data class CallCount(
    val call: Call,
    val count: Int
)

data class Call(
        val component: String,
        val method: String,
        val user: String
)
