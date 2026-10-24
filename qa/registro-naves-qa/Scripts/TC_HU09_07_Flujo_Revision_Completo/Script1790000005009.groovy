import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.FormDataBodyParameter
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.testobject.impl.HttpFormDataBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.model.FailureHandling
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

String base = 'http://localhost:8080'

/** Inicia sesion y devuelve la cookie JSESSIONID. */
def iniciarSesion = { String correo ->
    RequestObject login = new RequestObject('login')
    login.setRestUrl(base + '/api/auth/login')
    login.setRestRequestMethod('POST')
    login.setHttpHeaderProperties([
        new TestObjectProperty('Content-Type', ConditionType.EQUALS,
            'application/x-www-form-urlencoded')
    ])
    login.setBodyContent(new HttpTextBodyContent(
        'correo=' + correo + '&password=Navesitas2026*'))
    ResponseObject r = Http.enviar(login)
    return Http.sesion(r)
}

/** GET con la cookie de sesion (o sin ella si es null). */
def consultar = { String cookie, String ruta ->
    RequestObject get = new RequestObject('consultar')
    get.setRestUrl(base + ruta)
    get.setRestRequestMethod('GET')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    get.setHttpHeaderProperties(cabeceras)
    return Http.enviar(get)
}

/** POST con cuerpo JSON y la cookie de sesion (o sin ella si es null). */
def postJson = { String cookie, String ruta, String cuerpo ->
    RequestObject p = new RequestObject('post')
    p.setRestUrl(base + ruta)
    p.setRestRequestMethod('POST')
    List<TestObjectProperty> cabeceras = [
        new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
    ]
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    p.setHttpHeaderProperties(cabeceras)
    p.setBodyContent(new HttpTextBodyContent(cuerpo))
    return Http.enviar(p)
}

def json = { ResponseObject r -> new JsonSlurper().parseText(r.getResponseBodyContent()) }

/**
 * Sprint 6 (HU-12, D4): aprobar el tramite exige los cuatro obligatorios
 * vigentes en APROBADO. La DGMM los aprueba uno por uno (trámite En revision).
 */
def aprobarDocumentos = { String cookie, def naveId ->
    def vigentes = json(consultar(cookie, '/api/naves/' + naveId + '/documentos'))
    assert vigentes.size() == 4 : 'Se esperaban 4 documentos vigentes y hay ' + vigentes.size()
    vigentes.each { d ->
        ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/documentos/' + d.id + '/revision',
            '{"decision":"APROBAR"}')
        WS.verifyResponseStatusCode(r, 200)
    }
}

/** HU-09: POST /api/naves/{id}/tramite/estado con la accion y, si hay, la observacion. */
def accion = { String cookie, def naveId, String nombre, String observacion = null ->
    Map cuerpo = [accion: nombre]
    if (observacion != null) cuerpo.observacion = observacion
    ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/tramite/estado', JsonOutput.toJson(cuerpo))
    println(nombre + ' -> ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
    return r
}

/** HU-03: registra una nave con nombre unico y devuelve la respuesta. */
def registrarNave = { String cookie, String prefijo ->
    String nombre = prefijo + ' ' + System.currentTimeMillis()
    return postJson(cookie, '/api/naves', """{
  "nombre": "${nombre}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 3200.00,
  "tonelajeNeto": 1700.00,
  "eslora": 78.00,
  "manga": 13.00,
  "puntal": 6.50,
  "anioConstruccion": 2017,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2100.00
}""")
}


/**
 * TC-31 — HU-09 (D2): el camino largo hasta "Aprobada".
 *   Solicitud presentada --admitir--> En revision --observar--> Observada
 *   --subsanar--> En revision --aprobar--> Aprobada
 * P5: observar sin observacion -> 400.
 * La nave tiene los 4 documentos obligatorios, asi que el caso vale antes
 * y despues de la regla de documentos.
 */
def prep = WebUI.callTestCase(findTestCase('Test Cases/TC_AUX_Nave_Con_Documentos'), [:], FailureHandling.STOP_ON_FAILURE)
String agente = prep.cookie
def naveId = prep.naveId
String dgmm = iniciarSesion('revisor@navesitas.pa')
String observacion = 'El ITC no corresponde a la nave. Cargue la version vigente.'

WS.verifyResponseStatusCode(accion(dgmm, naveId, 'ADMITIR'), 200)

ResponseObject rSinObs = accion(dgmm, naveId, 'OBSERVAR', '   ')
WS.verifyResponseStatusCode(rSinObs, 400)
assert json(rSinObs).campos?.observacion : 'Falta el error del campo observacion'

ResponseObject rObservar = accion(dgmm, naveId, 'OBSERVAR', observacion)
WS.verifyResponseStatusCode(rObservar, 200)
WS.verifyEqual(json(rObservar).estado, 'OBSERVADA')
WS.verifyEqual(json(consultar(agente, '/api/naves/' + naveId + '/tramite')).acciones, ['SUBSANAR', 'DESISTIR'])

WS.verifyResponseStatusCode(accion(agente, naveId, 'SUBSANAR', 'Se cargo el ITC vigente.'), 200)

aprobarDocumentos(dgmm, naveId)   // Sprint 6 (HU-12, D4)

ResponseObject rAprobar = accion(dgmm, naveId, 'APROBAR')
WS.verifyResponseStatusCode(rAprobar, 200)
WS.verifyEqual(json(rAprobar).estado, 'APROBADA')
WS.verifyEqual(json(rAprobar).esFinal, true)

// Sprint 6 (HU-12, D5): aprobar el tramite abandera la nave (antes P6: no la tocaba).
WS.verifyEqual(json(consultar(agente, '/api/naves/' + naveId)).estado, 'ABANDERADA')

def h = json(consultar(agente, '/api/naves/' + naveId + '/tramite/historial'))
println('Historial: ' + h)
WS.verifyEqual(h*.accion, ['PRESENTAR', 'ADMITIR', 'OBSERVAR', 'SUBSANAR', 'APROBAR'])
WS.verifyEqual(h*.estadoNuevo, ['SOLICITUD_PRESENTADA', 'EN_REVISION', 'OBSERVADA', 'EN_REVISION', 'APROBADA'])
WS.verifyEqual(h*.estadoAnterior, [null, 'SOLICITUD_PRESENTADA', 'EN_REVISION', 'OBSERVADA', 'EN_REVISION'])
WS.verifyEqual(h*.rol, ['AGENTE_NAVIERO', 'FUNCIONARIO_DGMM', 'FUNCIONARIO_DGMM', 'AGENTE_NAVIERO', 'FUNCIONARIO_DGMM'])
WS.verifyEqual(h[2].observacion, observacion)
assert h.every { it.fecha } : 'Alguna entrada del historial no tiene fecha'
