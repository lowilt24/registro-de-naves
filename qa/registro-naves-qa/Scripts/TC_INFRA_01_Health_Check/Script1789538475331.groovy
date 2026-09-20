import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

WebUI.openBrowser('http://localhost:8080/actuator/health')
WebUI.verifyTextPresent('UP', false)
WebUI.closeBrowser()