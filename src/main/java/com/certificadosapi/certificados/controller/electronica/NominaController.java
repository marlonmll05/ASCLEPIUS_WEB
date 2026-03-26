package com.certificadosapi.certificados.controller.electronica;

import com.certificadosapi.certificados.service.electronica.NominaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

@RestController
@RequestMapping("/nomina")
public class NominaController {

    private static final Logger log = LoggerFactory.getLogger(NominaController.class);

    private final NominaService nominaService;

    @Autowired
    public NominaController(NominaService nominaService) {
        this.nominaService = nominaService;
    }

    @GetMapping("/GenerarNominaEmpleado")
    public ResponseEntity<?> generarNominaEmpleado(
            @RequestParam int IdEmpresaKey,
            @RequestParam int IdNomina,
            @RequestParam int IdContratoKey) {

        log.info("[NominaController] Solicitud recibida - IdEmpresaKey={} IdNomina={} IdContratoKey={}",
                IdEmpresaKey, IdNomina, IdContratoKey);

        try {
            ObjectMapper mapper = new ObjectMapper();

            // 1. Obtener URL y Token
            Map<String, String> datosConexion = nominaService.obtenerDatosConexion(IdNomina, IdContratoKey);
            String urlLatam    = datosConexion.get("urlLatam");
            String bearerToken = datosConexion.get("bearerToken");

            // 2. Construir JSON de nómina
            ObjectNode root = nominaService.construirJsonNomina(IdEmpresaKey, IdNomina, IdContratoKey);
            String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            log.debug("[NominaController] JSON construido:\n{}", jsonString);

            // 3. Enviar al PAC y procesar respuesta
            try {
                Map<String, Object> resultado = nominaService.enviarAlPac(IdNomina, IdContratoKey, jsonString, bearerToken, urlLatam);

                boolean fueConsulta = Boolean.TRUE.equals(resultado.get("fueConsulta"));
                String statusCode   = (String) resultado.get("statusCode");
                String body         = fueConsulta
                        ? (String) resultado.get("respuestaConsulta")
                        : (String) resultado.get("body");

                log.info("[NominaController] Resultado final - StatusCode={} fueConsulta={}", statusCode, fueConsulta);
                return ResponseEntity.ok(body);

            } catch (HttpClientErrorException | HttpServerErrorException e) {
                log.error("[NominaController] Error HTTP al enviar al PAC: {}", e.getMessage());
                String errorBody = e.getResponseBodyAsString();

                try {
                    JsonNode jsonBody = mapper.readTree(errorBody);
                    String message = jsonBody.has("message") ? jsonBody.get("message").asText() : "Sin mensaje";
                    log.warn("[NominaController] Mensaje de error PAC: {}", message);

                    if (jsonBody.has("errors")) {
                        JsonNode errorsNode = jsonBody.get("errors");
                        Iterator<Entry<String, JsonNode>> fields = errorsNode.fields();
                        while (fields.hasNext()) {
                            Entry<String, JsonNode> field = fields.next();
                            for (JsonNode contenido : field.getValue()) {
                                log.warn("[NominaController] Error campo '{}': {}", field.getKey(), contenido);
                            }
                        }
                    }
                } catch (Exception parseEx) {
                    log.error("[NominaController] Error parseando respuesta de error del PAC: {}", parseEx.getMessage());
                }

                return ResponseEntity.status(e.getStatusCode()).body("Error de validación: " + errorBody);
            }

        } catch (IllegalStateException e) {
            log.warn("[NominaController] Dato no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (Exception e) {
            log.error("[NominaController] Error general inesperado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error general: " + e.getMessage());
        }
    }
}