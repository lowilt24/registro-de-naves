import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-10 — HU-05, negativo: la suma de participaciones no puede pasar
 * del 100% de la nave.
 *
 * Criterio de aceptacion: el primer copropietario al 60% entra; el
 * segundo al 60% se rechaza con 409 (regla de negocio, no error de
 * formato) y la nave queda con un solo propietario.
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

String cuerpoNave = """{
  "nombre": "Nave HU05 Cuotas ${sufijo}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 4000.00,
  "tonelajeNeto": 2100.00,
  "eslora": 110.00,
  "manga": 18.00,
  "puntal": 9.00,
  "anioConstruccion": 2017,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 4500.00
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

// --- Primer copropietario: 60%. Debe entrar. ---
String primero = """{
  "nombre": "Copropietario Uno ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-C1-${sufijo}",
  "naveId": ${naveId},
  "porcentaje": 60.00
}"""

RequestObject crear1 = new RequestObject('primerCopropietario')
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

// --- Segundo copropietario: otro 60%. 60 + 60 = 120 > 100. ---
String segundo = """{
  "nombre": "Copropietario Dos ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Avenida Balboa, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-C2-${sufijo}",
  "naveId": ${naveId},
  "porcentaje": 60.00
}"""

RequestObject crear2 = new RequestObject('segundoCopropietario')
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
WS.verifyMatch(r2.getResponseBodyContent(), '.*[Pp]articipacion.*', true)

// La nave debe quedar con un solo propietario.
RequestObject listar = new RequestObject('listarPropietarios')
listar.setRestUrl(base + '/api/naves/' + naveId + '/propietarios')
listar.setRestRequestMethod('GET')
listar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rLista = WS.sendRequest(listar)
WS.verifyResponseStatusCode(rLista, 200)
WS.verifyEqual(new JsonSlurper().parseText(rLista.getResponseBodyContent()).size(), 1)
