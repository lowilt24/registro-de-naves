package navesitas

import com.kms.katalon.core.testobject.*
import com.kms.katalon.core.testobject.impl.*
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/** API HU-13, con el mismo manejo CSRF de TS06. Solo cuentas demo locales. */
class Hu13 {
    static final String BASE = 'http://localhost:8080'
    static def json(ResponseObject r) { new JsonSlurper().parseText(r.getResponseBodyContent()) }
    static ResponseObject pedir(String cookie, String ruta, Map cuerpo = null) {
        RequestObject r = new RequestObject('HU13')
        r.setRestUrl(BASE + ruta)
        r.setRestRequestMethod(cuerpo == null ? 'GET' : 'POST')
        def headers = []
        if(cookie) headers << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
        if(cuerpo != null) {
            headers << new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
            r.setBodyContent(new HttpTextBodyContent(JsonOutput.toJson(cuerpo)))
        }
        r.setHttpHeaderProperties(headers)
        Http.enviar(r)
    }
    static String login(String correo) {
        RequestObject r = new RequestObject('loginHU13')
        r.setRestUrl(BASE + '/api/auth/login'); r.setRestRequestMethod('POST')
        r.setHttpHeaderProperties([new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/x-www-form-urlencoded')])
        String clave = System.getenv('NAVESITAS_CLAVE') ?: 'Navesitas2026*'
        r.setBodyContent(new HttpTextBodyContent('correo=' + URLEncoder.encode(correo, 'UTF-8') + '&password=' + URLEncoder.encode(clave,'UTF-8')))
        String cookie = Http.sesion(Http.enviar(r))
        assert cookie : 'Login demo fallido'
        cookie
    }
    static def nave(String cookie, Map cambios = [:]) {
        Map datos = [nombre:'HU Trece Katalon ' + System.nanoTime(), tipo:'CARGA', servicio:'INTERNACIONAL',
            tonelajeBruto:499, tonelajeNeto:100, eslora:23.99, manga:10, puntal:5, anioConstruccion:2022,
            lugarConstruccion:'Panama', materialCasco:'Acero', tipoPropulsion:'Diesel', potenciaKw:1000] + cambios
        def r = pedir(cookie, '/api/naves', datos)
        WS.verifyResponseStatusCode(r, 201)
        json(r).id
    }
    static void preparar(String cookie, def id) {
        WS.verifyResponseStatusCode(pedir(cookie, '/api/propietarios', [nombre:'Naviera HU Trece', tipo:'JURIDICA',
            nacionalidad:'Panamena', domicilio:'Panama', paisConstitucion:'Panama', naveId:id]), 201)
        WS.verifyResponseStatusCode(pedir(cookie, '/api/naves/'+id+'/agente-residente',
            [nombre:'Bufete HU Trece', idoneidad:'IDN-'+System.nanoTime(), telefono:'+507 200-1234', correo:'hu13@bufete.pa']), 201)
    }
    static void cargar(String cookie, def id, String tipo) {
        File archivo = File.createTempFile('hu13-', '.pdf')
        try {
            archivo.setText('%PDF-1.4\n%%EOF\n','UTF-8')
            RequestObject r = new RequestObject('cargaHU13')
            r.setRestUrl(BASE + '/api/naves/'+id+'/documentos'); r.setRestRequestMethod('POST')
            def body = new HttpFormDataBodyContent([
                new FormDataBodyParameter('tipo', tipo, FormDataBodyParameter.PARAM_TYPE_TEXT),
                new FormDataBodyParameter('archivo', archivo.absolutePath, FormDataBodyParameter.PARAM_TYPE_FILE)])
            r.setHttpHeaderProperties([new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie),
                new TestObjectProperty('Content-Type', ConditionType.EQUALS, body.getContentType())])
            r.setBodyContent(body)
            WS.verifyResponseStatusCode(Http.enviar(r), 201)
        } finally { archivo.delete() }
    }
    static ResponseObject accion(String cookie, def id, String accion) {
        pedir(cookie, '/api/naves/'+id+'/tramite/estado', [accion:accion, observacion:'Prueba documental HU-13'])
    }
}
