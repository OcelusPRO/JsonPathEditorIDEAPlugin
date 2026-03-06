package fr.ftnl.jsonpatheditor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.LicensingFacade
import fr.ftnl.jsonpatheditor.internal.ActionLogic
import fr.ftnl.jsonpatheditor.internal.isPremiumActive

class SearchByValueAction : AnAction() {
    
    // Identifiant de la ToolWindow (on peut le cacher un peu ici aussi)
    private val TW_ID = "fr.ftnl.jsonpatheditor.JsonPathEditor"
    
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (isPremiumActive()) return ActionLogic.executeSearch(e, TW_ID)
        val actionManager = ActionManager.getInstance()
        val registerAction = actionManager.getAction("Register")
        if (registerAction != null) {
            actionManager.tryToExecute(registerAction, e.inputEvent, null, null, true)
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ActionLogic.checkVisibility(e)
    }

}