package it.unibo.sap.infrastructure.web.handlers

import infrastructure.web.sendReply
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import it.unibo.sap.application.RideService
import it.unibo.sap.application.exceptions.RideAlreadyEnded
import it.unibo.sap.domain.Ride
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Handler for ride-related requests.
 */
interface RideHandler {
    val rideService: RideService

    /**
     * Starts a new ride.
     * @param context the routing context
     */
    fun startNewRide(context: RoutingContext)

    /**
     * Gets a ride by its id.
     * @param context the routing context
     */
    fun getRide(context: RoutingContext)

    /**
     * Ends a ride by its id.
     * @param context the routing context
     */
    fun endRide(context: RoutingContext)
}

class RideHandlerImpl(override val rideService: RideService) : RideHandler {
    val logger: Logger = Logger.getLogger("[RideHandler]")

    override fun startNewRide(context: RoutingContext) {
        logger.log(Level.INFO, "New ride registration request")
        context.body().asJsonObject()?.apply {
            logger.log(Level.INFO, encodePrettily())
            val _userId: String? = getString("userId")
            val _escooterId: String? = getString("escooterId")
            _userId?.let { userId ->
                _escooterId?.let { escooterId ->
                    rideService.startNewRide(userId, escooterId)
                }
            }?.fold(
                onSuccess = { context.sendReply(JsonObject().put("result", "ok").put("rideId", it.id)) },
                onFailure = { context.sendReply(JsonObject().put("result", "error-saving-ride")) }
            )
        } ?: context.sendReply(JsonObject().put("result", "ERROR: some-fields-were-null"))
    }

    override fun getRide(context: RoutingContext) {
        logger.log(Level.INFO, "Get ride request")
        context.apply {
            logger.log(Level.INFO, currentRoute().path)
            val _id: String? = pathParam("rideId")
            _id?.let {
                rideService.getRide(it)
            }?.onSuccess { ride ->
                context.sendReply(
                    JsonObject().put("result", "ok").put("ride", ride.toJson())
                )
            }?.onFailure {
                context.sendReply(JsonObject().put("result", "ride-not-found"))
            } ?: context.sendReply(
                JsonObject().put("result", "ERROR: some-fields-were-null")
            )
        }
    }

    override fun endRide(context: RoutingContext) {
        logger.log(Level.INFO, "End ride request")
        context.apply {
            logger.log(Level.INFO, currentRoute().path)
            val _id: String? = pathParam("rideId")
            _id?.let {
                rideService.endRide(it)
            }?.onSuccess {
                context.sendReply(JsonObject().put("result", "ok"))
            }?.onFailure {
                when (it) {
                    RideAlreadyEnded() ->
                        context.sendReply(JsonObject().put("result", "ride-already-ended"))

                    else ->
                        context.sendReply(JsonObject().put("result", "ride-not-found"))
                }
            }
                ?: context.sendReply(
                    JsonObject().put("result", "ERROR: some-fields-were-null")
                )
        }
    }
}

/**
 * Creates a new ride handler.
 * @param rideService the ride service used to manage rides
 * @return the new ride handler
 */
fun RideHandler(rideService: RideService) = RideHandlerImpl(rideService)

/**
 * Converts a ride to a json object.
 * @return the json object
 */
fun Ride.toJson() =
    JsonObject().put("id", this.id).put("userId", this.userId).put("escooterId", this.escooterId)
        .put("startDate", this.startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .put("endDate", this.endDate?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .putNull("location")
