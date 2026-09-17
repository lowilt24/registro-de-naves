import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent

String base = 'http://localhost:8080'
String correo = 'prueba' + System.currentTimeMillis() + '@navesitas.pa'

String cuerpo = """{
  "correo": "${correo}",
  "password": "Navesitas2026*",
  "nombreCompleto": "Usuario de Prueba Katalon",
  "rol": "AGENTE_NAVIERO"
}"""

RequestObject registro = new RequestObject('registroUsuario')
registro.setRestUrl(base + '/api/auth/registro')
registro.setRestRequestMethod('POST')
registro.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
])
registro.setBodyContent(new HttpTextBodyContent(cuerpo))

ResponseObject r = WS.sendRequest(registro)
println('Status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 201)
WS.verifyMatch(r.getResponseBodyContent(), '.*AGENTE_NAVIERO.*', true)