package com.certificadosapi.certificados.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import com.certificadosapi.certificados.service.ValidadorService;

@RestController
@RequestMapping("/api/validador")
public class ValidadorController {
    
    private final ValidadorService validadorService;

    @Autowired
    public ValidadorController(ValidadorService validadorService) {
        this.validadorService = validadorService;
    }

    // ENDPOINT PARA ENVIAR FACTURA AL MINISTERIO
    @PostMapping("/subir")
    public ResponseEntity<String> subirArchivoJson(@RequestBody String jsonContenido, 
                                                  @RequestHeader("Authorization") String bearerToken, 
                                                  @RequestParam String nFact) {
        String resultado = validadorService.subirArchivoJson(jsonContenido, bearerToken, nFact);
        return ResponseEntity.ok(resultado);
    }

    // Método para exportar documento XML en Base64
    @GetMapping("/base64/{idMovDoc}")
    public ResponseEntity<String> exportDocXmlBase64(@PathVariable int idMovDoc) {
        String resultado = validadorService.exportDocXmlBase64(idMovDoc);
        return ResponseEntity.ok(resultado);
    }

    // Método para el login
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody String jsonBody) {
        String resultado = validadorService.login(jsonBody);
        return ResponseEntity.ok(resultado);
    }

    @RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
    return ResponseEntity.ok().build();
    }

    // Método para guardar la respuesta de la API (El CUV se guarda en una tabla y luego al descargar la factura aparece el TXT con el CUV)
    @PostMapping("/guardarrespuesta")
    public ResponseEntity<?> guardarRespuestaApi(@RequestBody Map<String, Object> payload) throws SQLException {
        
        String nFact = (String) payload.get("nFact");
        String mensajeRespuesta = (String) payload.get("mensajeRespuesta");
        
        String resultado = validadorService.guardarRespuestaApi(nFact, mensajeRespuesta);
        return ResponseEntity.ok(resultado);
    }

    // Metodo para actualizar la respuesta de la Api Docker para la tabla Rips_RespuestaApi
    @PostMapping("/actualizar-respuesta")
    public ResponseEntity<String> actualizarRespuestaApi(@RequestBody Map<String, String> body) {
        try {
            String nFact = body.get("nFact");
            String mensajeRespuesta = body.get("mensajeRespuesta");

            String resultado = validadorService.actualizarRespuestaApi(nFact, mensajeRespuesta);
            return ResponseEntity.ok(resultado);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar respuesta: " + e.getMessage());
        }
    }

    // Metodo para consultar el cuv al ministerio y retornar una respuesta
    @PostMapping("/consultar-cuv")
    public ResponseEntity<String> consultarCuv(
        @RequestBody Map<String, String> cuv,
        @RequestHeader("Authorization") String bearerToken
    ) {
        try {
            String resultado = validadorService.consultarCuv(cuv, bearerToken);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            if (e instanceof HttpStatusCodeException) {
                HttpStatusCodeException ex = (HttpStatusCodeException) e;
                return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
