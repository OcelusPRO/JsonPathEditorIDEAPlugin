package fr.ftnl.jsonpatheditor.internal

import com.intellij.ui.LicensingFacade
import java.util.Date


fun isPremiumActive(): Boolean {
    val facade = LicensingFacade.getInstance() ?: return false
    val expirationDate = facade.expirationDates[ActionLogic.getPluginName()]
    return expirationDate != null && expirationDate.after(Date())
}