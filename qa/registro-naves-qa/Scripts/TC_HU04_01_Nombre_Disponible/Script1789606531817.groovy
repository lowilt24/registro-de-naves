import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent

String base = 'http://localhost:8080'

// Desde el Sprint 3 todo /api/** exige sesion (remediacion del hallazgo
// SEC-04). Sin este paso la consulta responde 401 y el caso falla.
RequestObject login = new RequestObject('login')
login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')
login.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS,
        'application/x-www-form-urlencoded')
])
login.setBodyContent(new HttpTextBodyContent(
    'correo=agente@navesitas.pa&password=Navesitas2026*'))

ResponseObject rLogin = Http.enviar(login)
String cookie = Http.sesion(rLogin)

String nombre = 'Nave Inexistente ' + System.currentTimeMillis()

RequestObject consulta = new RequestObject('disponibilidad')
consulta.setRestUrl(base + '/api/naves/disponibilidad?nombre=' +
    java.net.URLEncoder.encode(nombre, 'UTF-8'))
consulta.setRestRequestMethod('GET')
consulta.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject r = Http.enviar(consulta)
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 200)
WS.verifyMatch(r.getResponseBodyContent(), '.*"disponible":true.*', true)
WS.verifyMatch(r.getResponseBodyContent(), '.*Nombre disponible.*', true)
