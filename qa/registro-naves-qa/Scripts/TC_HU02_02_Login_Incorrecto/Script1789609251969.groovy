import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent

String base = 'http://localhost:8080'

RequestObject login = new RequestObject('login')
login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')
login.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS,
        'application/x-www-form-urlencoded')
])
login.setBodyContent(new HttpTextBodyContent(
    'correo=agente@navesitas.pa&password=Navesitas2026*'))

ResponseObject rLogin = WS.sendRequest(login)
String cookie = rLogin.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)

RequestObject sesion = new RequestObject('sesion')
sesion.setRestUrl(base + '/api/auth/sesion')
sesion.setRestRequestMethod('GET')
sesion.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject r = WS.sendRequest(sesion)
println('Sesion: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 200)
WS.verifyMatch(r.getResponseBodyContent(), '.*"autenticado":true.*', true)
WS.verifyMatch(r.getResponseBodyContent(), '.*AGENTE_NAVIERO.*', true)