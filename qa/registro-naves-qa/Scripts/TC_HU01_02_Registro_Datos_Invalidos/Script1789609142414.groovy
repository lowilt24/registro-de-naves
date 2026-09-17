import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent

String base = 'http://localhost:8080'

String cuerpo = """{
  "correo": "esto-no-es-un-correo",
  "password": "123",
  "nombreCompleto": "Usuario Invalido",
  "rol": "AGENTE_NAVIERO"
}"""

RequestObject registro = new RequestObject('registroInvalido')
registro.setRestUrl(base + '/api/auth/registro')
registro.setRestRequestMethod('POST')
registro.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
])
registro.setBodyContent(new HttpTextBodyContent(cuerpo))

ResponseObject r = WS.sendRequest(registro)
println('Status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 400)
WS.verifyMatch(r.getResponseBodyContent(), '.*correo.*', true)