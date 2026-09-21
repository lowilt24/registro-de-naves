import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-11 — HU-05, negativo: no se puede registrar dos propietarios con la
 * misma identificacion (RUC, cedula o pasaporte).
 *
 * Las dos cuotas suman 80%, asi que la regla del 100% no interfiere: el
 * unico motivo posible de rechazo es la identificacion repetida.
 */

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

String sufijo = System.currentTimeMillis()
String identificacion = "RUC-DUP-${sufijo}"

String cuerpoNave = """{
  "nombre": "Nave HU05 Duplicada ${sufijo}",
  "tipo": "CARGA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 1800.00,
  "tonelajeNeto": 950.00,
  "eslora": 75.00,
  "manga": 13.00,
  "puntal": 6.50,
  "anioConstruccion": 2018,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2100.00
}"""

RequestObject crearNave = new RequestObject('crearNave')
crearNave.setRestUrl(base + '/api/naves')
crearNave.setRestRequestMethod('POST')
crearNave.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crearNave.setBodyContent(new HttpTextBodyContent(cuerpoNave))

ResponseObject rNave = WS.sendRequest(crearNave)
WS.verifyResponseStatusCode(rNave, 201)
def naveId = new JsonSlurper().parseText(rNave.getResponseBodyContent()).id

// --- Primer registro con esa identificacion: entra. ---
String primero = """{
  "nombre": "Original ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "${identificacion}",
  "naveId": ${naveId},
  "porcentaje": 40.00
}"""

RequestObject crear1 = new RequestObject('propietarioOriginal')
crear1.setRestUrl(base + '/api/propietarios')
crear1.setRestRequestMethod('POST')
crear1.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crear1.setBodyContent(new HttpTextBodyContent(primero))

ResponseObject r1 = WS.sendRequest(crear1)
println('Primero status: ' + r1.getStatusCode())
WS.verifyResponseStatusCode(r1, 201)

// --- Segundo registro con la MISMA identificacion: debe rechazarse. ---
String segundo = """{
  "nombre": "Duplicado ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Avenida Balboa, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "${identificacion}",
  "naveId": ${naveId},
  "porcentaje": 40.00
}"""

RequestObject crear2 = new RequestObject('propietarioDuplicado')
crear2.setRestUrl(base + '/api/propietarios')
crear2.setRestRequestMethod('POST')
crear2.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crear2.setBodyContent(new HttpTextBodyContent(segundo))

ResponseObject r2 = WS.sendRequest(crear2)
println('Segundo status: ' + r2.getStatusCode())
println('Respuesta: ' + r2.getResponseBodyContent())

WS.verifyResponseStatusCode(r2, 409)
WS.verifyMatch(r2.getResponseBodyContent(), '.*identificacion.*', true)

// Solo debe haber quedado el primero.
RequestObject listar = new RequestObject('listarPropietarios')
listar.setRestUrl(base + '/api/naves/' + naveId + '/propietarios')
listar.setRestRequestMethod('GET')
listar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rLista = WS.sendRequest(listar)
WS.verifyResponseStatusCode(rLista, 200)
WS.verifyEqual(new JsonSlurper().parseText(rLista.getResponseBodyContent()).size(), 1)
