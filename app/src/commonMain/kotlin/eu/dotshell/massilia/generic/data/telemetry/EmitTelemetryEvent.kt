package eu.dotshell.massilia.generic.data.telemetry

/**
 * Fire-and-forget telemetry emit. No-op if telemetry is uninitialised or the user opted out.
 * Now fully shared (the [TelemetryEmitter] it delegates to lives in commonMain).
 */
fun emitTelemetryEvent(event: TelemetryEvent) {
    TelemetryEmitter.emit(event)
}
