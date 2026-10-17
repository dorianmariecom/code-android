package com.codedorian

import dev.hotwire.core.turbo.visit.VisitProposal
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.routing.Router

/** Transfer cross-tab web visits without triggering the tab-click refresh. */
class TabRouteDecisionHandler : Router.RouteDecisionHandler {
    override val name = "tab-navigation"

    override fun matches(proposal: VisitProposal, configuration: NavigatorConfiguration): Boolean =
        TabLocations(configuration.startLocation).matchingIndex(proposal.location, listOf("/")) >= 0

    override fun handle(
        proposal: VisitProposal,
        configuration: NavigatorConfiguration,
        activity: HotwireActivity,
    ): Router.Decision {
        val mainActivity = activity as? MainActivity ?: return Router.Decision.NAVIGATE
        if (!mainActivity.shouldSwitchTab(proposal, configuration)) return Router.Decision.NAVIGATE
        mainActivity.routeToLocation(proposal.location, proposal.options, proposal.bundle)
        return Router.Decision.CANCEL
    }
}
