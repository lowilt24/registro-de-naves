package pa.amp.registro_naves.tramite;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import pa.amp.registro_naves.documento.*;
import pa.amp.registro_naves.nave.*;
import pa.amp.registro_naves.usuario.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class Sprint9IntegrationTest {
    static final String AGENTE="agente@navesitas.pa",DGMM="revisor@navesitas.pa",AUDITOR="auditor@navesitas.pa";
    static final ZoneId ZONA=ZoneId.of("America/Panama");
    @Autowired MockMvc mvc;
    @Autowired org.springframework.web.context.WebApplicationContext contexto;
    @Autowired JdbcTemplate jdbc;
    @Autowired NaveRepository naves;
    @Autowired TramiteRepository tramites;
    @Autowired DocumentoRepository documentos;
    @Autowired UsuarioRepository usuarios;
    LocalDate hoy;

    @BeforeEach void preparar() {
        // La configuracion compartida agrega _csrf como parametro incluso a GET.
        // Este cliente reproduce los filtros reales del navegador y mantiene CSRF
        // explicitamente en los POST, sin inyectar parametros ajenos al reporte.
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        // Aislar los agregados dentro de la transaccion; el rollback restaura los datos previos.
        for(String tabla:new String[]{"constancias_registro","documentos","historial_estados","tramites","agentes_residentes","nave_propietario","nave"})
            jdbc.update("delete from "+tabla);
        hoy=LocalDate.now(ZONA);
        Nave uno=nave(TipoNave.CARGA,EstadoNave.REGISTRADA,hoy.minusDays(2).atStartOfDay(ZONA).toOffsetDateTime());
        Nave dos=nave(TipoNave.CARGA,EstadoNave.RECHAZADA,hoy.minusDays(1).atTime(23,59,59,999000000).atZone(ZONA).toOffsetDateTime());
        Nave tres=nave(TipoNave.PASAJEROS,EstadoNave.REGISTRADA,hoy.atStartOfDay(ZONA).toOffsetDateTime());
        tramite(uno,EstadoTramite.EN_REVISION,hoy.minusDays(4));
        tramite(dos,EstadoTramite.EN_REVISION,hoy.minusDays(2));
        tramite(tres,EstadoTramite.APROBADA,hoy.minusDays(1));
        documento(uno,TipoDocumento.CERTIFICADO_PROPIEDAD,true,1,EstadoDocumento.PENDIENTE,uno.getCreadoEn());
        documento(uno,TipoDocumento.CERTIFICADO_PROPIEDAD,false,2,EstadoDocumento.PENDIENTE,uno.getCreadoEn());
        documento(uno,TipoDocumento.PODER_NOTARIAL,true,1,EstadoDocumento.APROBADO,uno.getCreadoEn());
        documento(dos,TipoDocumento.ITC,true,1,EstadoDocumento.PENDIENTE,dos.getCreadoEn());
        documento(tres,TipoDocumento.SMC,true,1,EstadoDocumento.PENDIENTE,tres.getCreadoEn());
    }
    Nave nave(TipoNave tipo,EstadoNave estado,OffsetDateTime fecha) {
        var n=new Nave(); n.setNombre("Nave QA "+UUID.randomUUID());n.setTipo(tipo);n.setServicio(TipoServicio.INTERNACIONAL);
        n.setTonelajeBruto(new BigDecimal("499"));n.setTonelajeNeto(new BigDecimal("250"));n.setEslora(new BigDecimal("24"));
        n.setManga(new BigDecimal("8"));n.setPuntal(new BigDecimal("4"));n.setAnioConstruccion(2020);n.setLugarConstruccion("Panamá");
        n.setMaterialCasco("Acero");n.setTipoPropulsion("Diésel");n.setPotenciaKw(new BigDecimal("750"));
        n.setEstado(estado);n.setCreadoEn(fecha);n.setRegistradoPor(usuarios.findByCorreoIgnoreCase(AGENTE).orElseThrow());
        return naves.saveAndFlush(n);
    }
    void tramite(Nave n,EstadoTramite estado,LocalDate fecha) {
        var t=new Tramite(n);t.cambiarA(estado);tramites.saveAndFlush(t);
        jdbc.update("update tramites set actualizado_en=? where id=?",fecha.atTime(12,0).atZone(ZONA).toOffsetDateTime(),t.getId());
    }
    void documento(Nave n,TipoDocumento tipo,boolean vigente,int version,EstadoDocumento estado,OffsetDateTime fecha) {
        var d=new Documento();d.setNave(n);d.setTipo(tipo);d.setVigente(vigente);d.setVersion(version);d.setNombreArchivo("qa.pdf");
        d.setRuta(UUID.randomUUID()+".pdf");d.setHashSha256("0".repeat(64));d.setTamanoBytes(10L);d.setCreadoEn(fecha);
        d.setCreadoPor(n.getRegistradoPor());if(estado==EstadoDocumento.APROBADO)d.marcarRevisado(estado,null,usuarios.findByCorreoIgnoreCase(DGMM).orElseThrow(),fecha);
        documentos.saveAndFlush(d);
    }
    ResultActions reporte(String correo,String parametros) throws Exception {
        return mvc.perform(get("/api/reportes/registro"+parametros).with(user(correo)));
    }
    ResultActions guia(String correo,String json) throws Exception {
        return mvc.perform(post("/api/guia-requisitos").with(user(correo)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }
    String consulta(String tipo,String servicio,String eslora,String gt) {
        return "{\"tipo\":\""+tipo+"\",\"servicio\":\""+servicio+"\",\"eslora\":"+eslora+",\"tonelajeBruto\":"+gt+"}";
    }

    @ParameterizedTest @ValueSource(strings={DGMM,AUDITOR})
    void reportesConDatosRealesYPromedioDeEspera(String correo) throws Exception {
        reporte(correo,"").andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'CARGA')].cantidad",contains(2)))
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'PASAJEROS')].cantidad",contains(1)))
            .andExpect(jsonPath("$.navesPorEstado[?(@.codigo == 'REGISTRADA')].cantidad",contains(2)))
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'EN_REVISION')].cantidad",contains(2)))
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'EN_REVISION')].diasPromedioEspera",contains(3.0)))
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'APROBADA')].diasPromedioEspera",contains(nullValue())))
            .andExpect(jsonPath("$.documentosPendientes[?(@.codigo == 'CERTIFICADO_PROPIEDAD')].cantidad",contains(1)))
            .andExpect(jsonPath("$.documentosPendientes[?(@.codigo == 'PODER_NOTARIAL')].cantidad",contains(0)));
    }
    @Test void fechasInclusivasEnPanamaYFechaPropiaDeCadaBloque() throws Exception {
        reporte(DGMM,"?desde="+hoy.minusDays(2)+"&hasta="+hoy.minusDays(1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'CARGA')].cantidad",contains(2)))
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'PASAJEROS')].cantidad",contains(0)))
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'EN_REVISION')].cantidad",contains(1)))
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'EN_REVISION')].diasPromedioEspera",contains(2.0)))
            .andExpect(jsonPath("$.documentosPendientes[?(@.codigo == 'SMC')].cantidad",contains(0)));
    }
    @Test void aceptaUnSoloExtremo() throws Exception {
        reporte(DGMM,"?desde="+hoy).andExpect(status().isOk())
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'PASAJEROS')].cantidad",contains(1)))
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'CARGA')].cantidad",contains(0)));
        reporte(AUDITOR,"?hasta="+hoy.minusDays(2)).andExpect(status().isOk())
            .andExpect(jsonPath("$.navesPorTipo[?(@.codigo == 'CARGA')].cantidad",contains(1)));
    }
    @Test void rangoVacioDevuelveCerosSinPromediosInventados() throws Exception {
        reporte(DGMM,"?desde=2000-01-01&hasta=2000-01-31").andExpect(status().isOk())
            .andExpect(jsonPath("$.navesPorTipo[*].cantidad",everyItem(is(0))))
            .andExpect(jsonPath("$.tramitesPorEstado[*].cantidad",everyItem(is(0))))
            .andExpect(jsonPath("$.tramitesPorEstado[*].diasPromedioEspera",everyItem(nullValue())))
            .andExpect(jsonPath("$.documentosPendientes[*].cantidad",everyItem(is(0))));
    }
    @Test void esperaDeFechaFuturaNoEsNegativa() throws Exception {
        jdbc.update("update tramites set actualizado_en=? where estado='EN_REVISION'",hoy.plusDays(2).atStartOfDay(ZONA).toOffsetDateTime());
        reporte(DGMM,"").andExpect(status().isOk())
            .andExpect(jsonPath("$.tramitesPorEstado[?(@.codigo == 'EN_REVISION')].diasPromedioEspera",contains(0.0)));
    }
    @ParameterizedTest @ValueSource(strings={"?desde=mal", "?hasta=2026-02-30", "?desde=2026-13-01", "?desde=2026-02-02&hasta=2026-01-01",
        "?desde=", "?otro=1", "?desde=2026-01-01&desde=2026-02-01", "?desde=0000-01-01", "?hasta=%2B10000-01-01"})
    void filtrosInvalidos400EnJsonYCsv(String parametros) throws Exception {
        reporte(DGMM,parametros).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/registro.csv"+parametros).with(user(AUDITOR))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={AGENTE,"admin@navesitas.pa"})
    void otrosRoles403InclusoConFiltroInvalido(String correo) throws Exception {
        reporte(correo,"?desde=mal").andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/registro.csv").with(user(correo))).andExpect(status().isForbidden());
    }
    @Test void sinSesion401EnAmbasHistorias() throws Exception {
        mvc.perform(get("/api/reportes/registro")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reportes/registro.csv")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/guia-requisitos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(consulta("CARGA","INTERNACIONAL","24","499"))).andExpect(status().isUnauthorized());
    }
    @Test void csvMismosFiltrosUtf8SinDatosPersonales() throws Exception {
        var respuesta=mvc.perform(get("/api/reportes/registro.csv?desde="+hoy.minusDays(2)+"&hasta="+hoy.minusDays(1))
                .with(user(AUDITOR))).andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition",containsString("reporte-registro.csv"))).andReturn().getResponse();
        String csv=new String(respuesta.getContentAsByteArray(),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFDesde,Hasta,").contains("\"Naves por tipo\",\"CARGA\",\"2\",\"\"",
            "\"Trámites por estado\",\"EN_REVISION\",\"1\",\"2.00\"")
            .doesNotContain("Nave QA",AGENTE,"password","token","Observación");
    }
    @Test void valorLegadoSePresentaComoRegistrada() throws Exception {
        jdbc.update("update nave set estado='ABANDERADA' where tipo='PASAJEROS'");
        reporte(DGMM,"").andExpect(status().isOk()).andExpect(jsonPath("$.navesPorEstado[?(@.codigo == 'REGISTRADA')].cantidad",contains(2)))
            .andExpect(jsonPath("$.navesPorEstado[?(@.codigo == 'ABANDERADA')]",hasSize(0)));
    }
    @ParameterizedTest @CsvSource({"CARGA,INTERNACIONAL,23.99,499,NO_APLICA,NO_APLICA", "CARGA,INTERNACIONAL,24,499,OBLIGATORIO,NO_APLICA",
        "CARGA,INTERNACIONAL,24.01,500,OBLIGATORIO,OBLIGATORIO", "CARGA,INTERNACIONAL,23.99,501,NO_APLICA,OBLIGATORIO",
        "PASAJEROS,INTERNACIONAL,23.99,100,NO_APLICA,OBLIGATORIO", "MIXTA,INTERNACIONAL,24,500,REQUIERE_REVISION,REQUIERE_REVISION",
        "CARGA,CABOTAJE,24,500,REQUIERE_REVISION,REQUIERE_REVISION", "CARGA,MIXTO,24,500,REQUIERE_REVISION,REQUIERE_REVISION"})
    void guiaUsaDecisionesDeHu13(String tipo,String servicio,String eslora,String gt,String itc,String smc) throws Exception {
        guia(AGENTE,consulta(tipo,servicio,eslora,gt)).andExpect(status().isOk())
            .andExpect(jsonPath("$.documentos[0].estado").value("OBLIGATORIO"))
            .andExpect(jsonPath("$.documentos[1].estado").value("OBLIGATORIO"))
            .andExpect(jsonPath("$.documentos[2].estado").value(itc)).andExpect(jsonPath("$.documentos[3].estado").value(smc))
            .andExpect(jsonPath("$.requiereRevision").value(itc.equals("REQUIERE_REVISION")||smc.equals("REQUIERE_REVISION")))
            .andExpect(jsonPath("$.pasos",hasSize(7)));
    }
    @Test void faltanMedidasNoSeAsumeExencionYNoSeEscribe() throws Exception {
        long antesNaves=naves.count(),antesTramites=tramites.count(),antesDocs=documentos.count();
        guia(AGENTE,consulta("CARGA","INTERNACIONAL","null","null")).andExpect(status().isOk())
            .andExpect(jsonPath("$.requiereRevision").value(true))
            .andExpect(jsonPath("$.documentos[2].estado").value("REQUIERE_REVISION"))
            .andExpect(jsonPath("$.documentos[3].estado").value("REQUIERE_REVISION"));
        guia(AGENTE,consulta("PASAJEROS","INTERNACIONAL","null","null")).andExpect(status().isOk())
            .andExpect(jsonPath("$.documentos[3].estado").value("OBLIGATORIO"));
        assertThat(naves.count()).isEqualTo(antesNaves);assertThat(tramites.count()).isEqualTo(antesTramites);assertThat(documentos.count()).isEqualTo(antesDocs);
    }
    @ParameterizedTest @ValueSource(strings={"{\"tipo\":\"TANQUERO\",\"servicio\":\"INTERNACIONAL\"}","{\"tipo\":\"YATE\",\"servicio\":\"INTERNACIONAL\"}",
        "{\"tipo\":null,\"servicio\":\"INTERNACIONAL\"}","{\"tipo\":\"CARGA\"}","{\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"eslora\":0}",
        "{\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"tonelajeBruto\":-1}","{\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"eslora\":24.001}",
        "{\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"eslora\":1000000}"})
    void guiaRechazaDatosInvalidos400(String cuerpo) throws Exception { guia(AGENTE,cuerpo).andExpect(status().isBadRequest()); }
    @ParameterizedTest @ValueSource(strings={DGMM,AUDITOR,"admin@navesitas.pa"})
    void guiaSoloParaAgente(String correo) throws Exception {
        guia(correo,consulta("CARGA","INTERNACIONAL","24","499")).andExpect(status().isForbidden());
    }
    @Test void paginasRestringidasPorRol() throws Exception {
        mvc.perform(get("/reportes.html").with(user(DGMM).roles("FUNCIONARIO_DGMM"))).andExpect(status().isOk());
        mvc.perform(get("/reportes.html").with(user(AUDITOR).roles("AUDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/reportes.html").with(user(AGENTE).roles("AGENTE_NAVIERO"))).andExpect(status().isForbidden());
        mvc.perform(get("/guia-requisitos.html").with(user(AGENTE).roles("AGENTE_NAVIERO"))).andExpect(status().isOk());
        mvc.perform(get("/guia-requisitos.html").with(user(DGMM).roles("FUNCIONARIO_DGMM"))).andExpect(status().isForbidden());
    }
}
