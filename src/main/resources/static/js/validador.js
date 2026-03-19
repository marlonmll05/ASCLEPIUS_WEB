// ==========================================================
// VALIDACIÓN Y CONFIGURACIÓN INICIAL
// ==========================================================

/**
 * Valida los tokens de autenticación almacenados
 * Redirige al login correspondiente si falta algún token
 */

const tokenSQL = localStorage.getItem('tokenSQL');
const token = sessionStorage.getItem('token');

if (!tokenSQL) {
localStorage.clear();
window.location.href = 'loginsql.html';
}

if (!token) {
sessionStorage.removeItem('token');
sessionStorage.removeItem('pestanaActiva');
window.location.href = 'login.html';
}

if (!sessionStorage.getItem('pestanaActiva')) {
sessionStorage.clear();
window.location.href = 'login.html';
}

sessionStorage.setItem('pestanaActiva', 'true');

// ==========================================================
// VARIABLES GLOBALES
// ==========================================================
const host = window.location.hostname;


// ==========================================================
// INICIALIZACIÓN DEL DOM
// ==========================================================

/**
 * Configura eventos y valores iniciales al cargar el documento
 * - Establece fechas por defecto (hoy)
 * - Carga terceros disponibles
 * - Activa verificación de checkboxes
 */
document.addEventListener('DOMContentLoaded', () => {
    const fechaDesdeInput = document.getElementById('fechaDesde');
    const today = new Date().toISOString().split('T')[0];
    fechaDesdeInput.value = today;

    const fechaHastaInput = document.getElementById('fechaHasta');
    fechaHastaInput.value = today;

    cargarTerceros();


    document.addEventListener('change', function (e) {
        if (e.target.classList.contains('filaCheckbox') || e.target.id === 'selectAll') {
            verificarCheckboxes();
        }
    });
});

// ==========================================================
// EVENT LISTENERS
// ==========================================================

/**
 * Maneja el envío del formulario de búsqueda de facturas
 * Valida fechas, construye parámetros y muestra resultados en tabla
 */
document.getElementById('facturasForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const submitBtn = e.submitter || document.querySelector('#facturasForm button[type="submit"]');
    const originalHTML = submitBtn ? submitBtn.innerHTML : '';
    
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = 'Buscando...';
        submitBtn.style.opacity = '0.6';
        submitBtn.style.cursor = 'not-allowed';
    }

    try {
        const fechaDesde = document.getElementById('fechaDesde').value;
        const fechaHasta = document.getElementById('fechaHasta').value;
        const tipoFechaCheckbox = document.getElementById('tipoFecha');


        if (!fechaDesde) {
            showToast('Error', 'La Fecha Desde es obligatoria.', 'error');
            return;
        }
        if (!fechaHasta) {
            showToast('Error', 'La Fecha Hasta es obligatoria.', 'error');
            return;
        }
        if (new Date(fechaDesde) > new Date(fechaHasta)) {
            showToast('Error', 'La Fecha Desde no puede ser mayor que la Fecha Hasta.', 'error');
            return;
        }

        const formData = new FormData(e.target);
        const params = new URLSearchParams();

        params.append('tipoFecha', tipoFechaCheckbox.checked)

        for (const [key, value] of formData.entries()) {
            if (value.trim()) params.append(key, value);
        }

        showToast('Procesando', 'Buscando facturas...', 'success', 2000);

        const response = await fetch(`/filtros/facturas?${params.toString()}`);
        const data = await response.json();

        if (!response.ok) {
            showToast('Error', typeof data === 'string' ? data : JSON.stringify(data), 'error');
            return;
        }

        const head = document.getElementById('tablaHead');
        const body = document.getElementById('tablaBody');
        const accionesDiv = document.getElementById('accionesDiv');

        accionesDiv.style.display = 'block';
        head.innerHTML = '';
        body.innerHTML = '';

        if (data.length === 0) {
            showToast('Error', 'No se encontraron resultados.', 'error')
            body.innerHTML = `<tr><td colspan="10" class="empty-state">No se encontraron resultados</td></tr>`;
            return;
        }

        const headers = Object.keys(data[0]).filter(h => h.toLowerCase() !== 'nomcontrato' && h.toLowerCase() !== 'idmovdoc' && h.toLowerCase() !== 'idtercerokey' && h.toLowerCase() !== 'nocontrato');
        head.innerHTML = '<tr><th class="checkbox-cell"><input type="checkbox" id="selectAll" onclick="toggleSelectAll(this)"></th>' +
            headers.map(h => `<th>${h}</th>`).join('') +
            '<th class="col-descripcion">Descripción</th>' + 
            '<th>Observaciones</th>' +
            '<th>Estado</th>' +
            '<th>CUV</th>' +
            '<th class="accionis">Acciones</th></tr>';

        const filasPromises = data.map(async (row, index) => {
            const idMovDoc = row.idMovDoc || row.IdMovDoc || row.IDMOVDOC || row.ID || '';
            const nFact = row.nFact || row.NFact || row.NFACT || row.NoFactura || '';
            const rowCells = headers.map(h => `<td>${row[h] ?? ''}</td>`).join('');
            const descripcion = row.descripcion || '';

            const filaHTML = 
            `<tr data-nfact="${nFact}">
                <td class="checkbox-cell"><input type="checkbox" class="filaCheckbox custom-checkbox" value="${idMovDoc}"></td>
                ${rowCells}
                <td class="col-descripcion">${descripcion}</td>
                <td>${row.observaciones || ''}</td>
                <td class="estado-cell">${row.estado || 'Pendiente'}</td>
                <td class="cuv-cell"></td>
                <td>
                <div class="button-flex">
                    <button class="button button-small button-success" onclick="enviarPaquete('${idMovDoc}', this)">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M22 2L11 13" />
                            <path d="M22 2L15 22L11 13L2 9L22 2Z" />
                        </svg>
                        Enviar
                    </button>
                    <button class="button button-small button-success" onclick="DescargarPaquete(this)" data-id="${idMovDoc}">
                        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"></path>
                        <polyline points="7 10 12 15 17 10"></polyline>
                        <line x1="12" y1="15" x2="12" y2="3"></line>
                        </svg>
                        Descargar
                        Paquete
                    </button>
                </div>
                    <div style="display: flex; justify-content: center; align-items: center; gap: 16px; margin-top: 20px;">
                    <button 
                        class="button button-small" 
                        onclick="verJSON('${idMovDoc}')" 
                        style="display: flex; flex-direction: column; align-items: center; min-width: 88px; padding: 8px 12px;">
                        <span>Ver</span>
                        <span style="font-size: 13px;">JSON</span>
                    </button>

                    <button 
                        class="button button-small descargar-logs-btn" 
                        style="display: none; flex-direction: column; align-items: center; min-width: 88px; padding: 8px 18px;" 
                        onclick="descargarLogsGenerales(this)">
                        <span>Descargar</span>
                        <span style="font-size: 13px;">Logs</span>
                    </button>
                    </div>
                </td>
            </tr>`;

            return { html: filaHTML, nFact: nFact };
        });

        const filas = await Promise.all(filasPromises);
        body.innerHTML = filas.map(f => f.html).join('');

        const toast = showToast('Procesando', 'Cargando CUVs...', 'success', 999999, true);

        await procesarFilasEnLotes(filas, body, 30, toast);

        showToast('Búsqueda completada', `Se encontraron ${data.length} resultados`, 'success');

    } catch (err) {
        showToast('Error', "Error en la solicitud: " + err.message, 'error');
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalHTML;
            submitBtn.style.opacity = '1';
            submitBtn.style.cursor = 'pointer';
        }
    }
});

/**
 * Carga contratos asociados al tercero seleccionado
 * Se activa al cambiar el select de terceros
 */

document.getElementById('idTercero').addEventListener('change', async function () {
    const idTerceroKey = this.value;
    const selectContratos = document.getElementById('noContrato');
    selectContratos.innerHTML = '<option value="">Seleccione un contrato</option>';

    if (idTerceroKey) {
        try {
            const response = await fetch(`/filtros/contratos?idTerceroKey=${idTerceroKey}`);
            const data = await response.json();

            data.forEach(contrato => {
                const option = document.createElement('option');
                option.value = contrato.noContrato;
                option.textContent = contrato.nomContrato;
                selectContratos.appendChild(option);
            });
        } catch (error) {
            console.error("Error al cargar contratos:", error);
        }
    }
});


/**
 * Activa visualmente los botones de filtro por estado
 * Remueve la clase 'activo' de otros botones al seleccionar uno
 */
document.querySelectorAll('.button-secondary').forEach(btn => {
    btn.addEventListener('click', function () {

        document.querySelectorAll('.button-secondary').forEach(b => b.classList.remove('activo'));

        if (!this.textContent.toLowerCase().includes('deseleccionar')) {
        this.classList.add('activo');
        }
    });
});




// ==========================================================
// FUNCIONES DE GESTIÓN DE CUV
// ==========================================================

/**
 * Procesa múltiples filas de facturas en lotes para cargar sus CUVs.
 * Divide el procesamiento en grupos más pequeños para evitar sobrecarga del navegador.
 * 
 * @param {Array} filas - Array de objetos con datos de las facturas a procesar
 * @param {HTMLElement} body - Elemento tbody de la tabla donde se mostrarán los resultados
 * @param {number} [loteSize=30] - Tamaño del lote (cantidad de filas a procesar simultáneamente)
 * @param {HTMLElement|null} [toast=null] - Elemento toast para mostrar el progreso de la operación
 * @returns {Promise<void>}
 */
async function procesarFilasEnLotes(filas, body, loteSize = 30, toast = null) {
    for (let i = 0; i < filas.length; i += loteSize) {
        const lote = filas.slice(i, i + loteSize);
        const promesas = lote.map(async (filaData, index) => {
            const fila = body.children[i + index];
            if (filaData.nFact) {
                await actualizarCUVEnFila(fila, filaData.nFact);
            } else {
                const cuvCell = fila.querySelector('.cuv-cell');
                const enviarBtn = fila.querySelector('button[onclick*="enviarPaquete"]');
                if (cuvCell) cuvCell.innerHTML = '';
                if (enviarBtn) {
                    enviarBtn.disabled = false;
                    enviarBtn.classList.remove('button-disabled');
                }
            }
        });

        await Promise.all(promesas);

        if (toast) {
            const progreso = Math.min(100, Math.round(((i + lote.length) / filas.length) * 100));
            actualizarToastProgreso(toast, progreso);
        }
    }

    if (toast) {
        actualizarToastProgreso(toast, 100);
        const mensaje = toast.querySelector('.toast-content p');
        if (mensaje) mensaje.textContent = 'CUVs Cargados correctamente';

        setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }
        }, 3000); 
    }
}

/**
 * Obtiene el CUV de una factura desde el servidor
 * @param {string} nFact - Número de factura
 * @returns {Promise<string>} CUV o cadena vacía si no existe
 */
async function obtenerCUV(nFact) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/api/sql/cuv?nFact=${nFact}`;
    
    try {
        const response = await fetch(url);
        if (!response.ok) {
            return '';
        }
        const data = await response.json();
        return data.Rips_CUV || '';
    } catch (error) {
        return '';
    }
}

/**
 * Actualiza el CUV en la fila de la tabla visual
 * Agrega botón de copiar y cambia el estado visual
 * @param {HTMLElement} fila - Elemento TR de la tabla
 * @param {string} nFact - Número de factura
 * @returns {Promise<void>}
 */
async function actualizarCUVEnFila(fila, nFact) {
    const cuvCell = fila.querySelector('.cuv-cell');
    if (cuvCell) {
        const cuv = await obtenerCUV(nFact);

        if (cuv && cuv.trim() !== '') {
            cuvCell.innerHTML = `
                <span class="valor-cuv">${cuv}</span>
                <button class="btn-copiar-cuv" title="Copiar CUV" style="margin-left:5px; cursor:pointer;">📋</button>
            `;

            const copiarBtn = cuvCell.querySelector('.btn-copiar-cuv');
            copiarBtn.addEventListener('click', () => {
                copiarAlPortapapeles(cuv)
                    .then(() => showToast('Copiado', `CUV copiado: ${cuv}`, 'success'))
                    .catch(err => showToast('Error', 'No se pudo copiar el CUV', 'error'));
                    
            });
            
        } else {
            cuvCell.innerHTML = '';
        }

        const enviarBtn = fila.querySelector('button[onclick*="enviarPaquete"]');
        const estadoCell = fila.querySelector('.estado-cell');
        fila.classList.remove('fila-verde');

        if (cuv && cuv.trim() !== '') {
            fila.classList.add('fila-verde');
            if (enviarBtn) {
                enviarBtn.disabled = true;
                enviarBtn.classList.add('button-disabled');
                enviarBtn.title = 'Ya enviado - CUV: ' + cuv;
            }
            if (estadoCell) {
                estadoCell.textContent = 'Enviado';
                estadoCell.classList.add('estado-verde');
            }
        } else {
            if (enviarBtn) {
                enviarBtn.disabled = false;
                enviarBtn.classList.remove('button-disabled');
                enviarBtn.title = 'Enviar paquete';
            }
        }
    }
}

/**
 * Agrega el CUV completo en Factura Final y Rips_Transaccion
 * @param {string} nFact - Número de factura
 * @param {string} cuv - Código Único de Validación
 * @param {number} idEstadoValidacion - ID del estado (2=Editado, 3=Enviado, 4=Rechazado)
 * @returns {Promise<boolean>} True si fue exitoso
 */
async function agregarCUVCompleto(nFact, cuv, idEstadoValidacion) {
    let agregadoExitoso = false;
    
    const urlAgregar = `https://${host}:9876/api/sql/agregarcuv?nFact=${nFact}&ripsCuv=${cuv}`;
    try {
        const agregarResponse = await fetch(urlAgregar, { method: 'POST' });
        if (agregarResponse.ok) {
            console.log(`CUV agregado en Factura Final para NFact ${nFact}`);
            agregadoExitoso = true;
        } else {
            console.error('⚠ Error al agregar CUV para Factura Final');
            return false;
        }
    } catch (error) {
        console.error('⚠ Error en la solicitud agregarcuv:', error);
        return false;
    }

    if (agregadoExitoso) {
        const urlActualizar = `https://${host}:9876/api/sql/actualizarcuvrips?nFact=${nFact}&cuv=${cuv}&idEstadoValidacion=${idEstadoValidacion}`;
        try {
            const actualizarResponse = await fetch(urlActualizar, { 
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            if (actualizarResponse.ok) {
                console.log(`CUV agregado en Rips_Transaccion para NFact ${nFact} (Estado: ${idEstadoValidacion})`);
            } else {
                const errorText = await actualizarResponse.text();
                console.error('⚠ Error al agregar CUV en Rips_Transaccion:', errorText);

            }
        } catch (error) {
            console.error('⚠ Error en la solicitud agregarcuvrips:', error);

        }
    }

    return agregadoExitoso;
}

/**
 * Actualiza solo el estado de validación 
 * @param {string} nFact - Número de factura
 * @param {number} idEstadoValidacion - ID del estado (2=Editado, 3=Enviado, 4=Rechazado)
 * @returns {Promise<boolean>} True si fue exitoso
 */

async function actualizarSoloEstado(nFact, idEstadoValidacion) {
    try {
        const urlActualizar = `https://${host}:9876/api/sql/actualizarcuvrips?nFact=${nFact}&cuv=&idEstadoValidacion=${idEstadoValidacion}`;
        const response = await fetch(urlActualizar, { 
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (response.ok) {
            console.log(`Estado actualizado a ${idEstadoValidacion} para NFact ${nFact}`);
            return true;
        } else {
            const errorText = await response.text();
            console.error('⚠ Error al actualizar estado:', errorText);
            return false;
        }
    } catch (error) {
        console.error('⚠ Error en la solicitud de actualización de estado:', error);
        return false;
    }
}

/**
 * Consulta el estado de validación actual de una factura
 * @param {string} nFact - Número de factura
 * @returns {Promise<number|null>} ID del estado o null si hay error
 */

async function obtenerEstadoValidacion(nFact) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/api/sql/estadovalidacion?nFact=${nFact}`;
    
    try {
        const response = await fetch(url);
        if (!response.ok) {
            console.error('Error al consultar estado de validación:', response.status);
            return null;
        }
        const data = await response.json(); 
        return data.idEstadoValidacion;
    } catch (error) {
        console.error('Error al obtener estado de validación:', error);
        return null;
    }
}


// ==========================================================
// FUNCIONES DE VISUALIZACIÓN Y EDICIÓN DE JSON
// ==========================================================

/**
 * Abre un modal para ver y editar el JSON de una factura
 * Permite búsqueda, colapso de arrays grandes y edición de campos
 * @param {string} idMovDoc - ID del movimiento/documento
 * @returns {Promise<void>}
 */
async function verJSON(idMovDoc) { 
    const host = window.location.hostname;
    const button = document.querySelector(`button[onclick="verJSON('${idMovDoc}')"]`);

    let originalHTML = '';
    if (button) {
        originalHTML = button.innerHTML;
        button.disabled = true;
        button.innerHTML = 'Ver JSON';
        button.style.opacity = '0.6';
        button.style.cursor = 'not-allowed';

        button.innerHTML = `
            <span>Ver</span>
            <span style="font-size: 13px;">JSON</span>
        `;
        }

    try {
        const fila = button.closest('tr');
        const nFact = fila.querySelector('td:nth-child(2)').textContent;
        const cuvCell = fila.querySelector('.cuv-cell');
        const cuvValue = cuvCell ? cuvCell.textContent.trim() : '';
        const tieneCUV = cuvValue !== '' && cuvValue !== null && cuvValue !== undefined;

        const ejecutarRipsUrl = `https://${host}:9876/api/sql/ejecutarRips?Nfact=${nFact}`;
        const ripsResp = await fetch(ejecutarRipsUrl);
        if (!ripsResp.ok) {
            const errorText = await ripsResp.text();
            const erroresPersonalizados = [
                {
                    condicion: (msg) => msg.includes("tipoDocumentoIdentificacion"),
                    mensaje: "No se puede insertar NULL en la columna 'tipoDocumentoIdentificacion', tabla 'IPSoft100_ST.dbo.Rips_Procedimientos"
                },
            ];

            let mensajeAmigable = erroresPersonalizados.find(e => e.condicion(errorText))?.mensaje;
            if (!mensajeAmigable) {
                mensajeAmigable = errorText.length > 150 ? errorText.slice(0, 150) + "..." : errorText;
            }

            showToast('Error', `No se pudo ejecutar Rips: ${mensajeAmigable}`, 'error');
            return;
        }

        const url = `https://${host}:9876/facturas/generarjson/${idMovDoc}`;
        const response = await fetch(url);
        if (!response.ok) {
            const errorText = await response.text();
            showToast('Error', `No se pudo obtener el JSON: ${errorText}`, 'error');
            return;
        }

        const data = await response.json();
        const pretty = JSON.stringify(data, null, 2);
        
        const totalLines = pretty.split('\n').length;
        const shouldCollapseArrays = totalLines >= 5000;

        const overlay = document.createElement('div');
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.1)';
        overlay.style.zIndex = '9998';
        overlay.style.display = 'flex';
        overlay.style.justifyContent = 'center';
        overlay.style.alignItems = 'flex-start';
        overlay.style.paddingTop = '5%';

        const modal = document.createElement('div');
        modal.style.backgroundColor = '#fff';
        modal.style.border = '1px solid #ccc';
        modal.style.padding = '0';
        modal.style.zIndex = '9999';
        modal.style.maxHeight = '90vh';
        modal.style.overflow = 'hidden';
        modal.style.width = '70%';
        modal.style.maxWidth = '1200px';
        modal.style.boxShadow = '0 2px 10px rgba(0,0,0,0.3)';
        modal.style.borderRadius = '8px';
        modal.style.position = 'relative';

        modal.innerHTML = `
            <style>
                .collapse-btn {
                    background: none;
                    border: none;
                    color: #007bff;
                    cursor: pointer;
                    font-family: monospace;
                    font-size: 12px;
                    margin-right: 5px;
                    padding: 0;
                    text-decoration: underline;
                }
                .collapse-btn:hover {
                    color: #0056b3;
                }
                .collapsed-content {
                    color: #999;
                    font-style: italic;
                }
                .json-line {
                    line-height: 1.4;
                }
            </style>
            <div class="verjson-header">
                <p class="verjson-title">JSON de: ${nFact} ${shouldCollapseArrays ? '(' + totalLines + ' líneas)' : ''}</p>
                <div class="verjson-toolbar">
                    <input type="text" id="jsonSearchInput" class="verjson-search" placeholder="Buscar en el JSON...">
                    <button id="prevMatch" class="navbtn" style="display: none;">←</button>
                    <div id="matchCounter">0 de 0</div>
                    <button id="nextMatch" class="navbtn" style="display: none;">→</button>
                    <button id="toggleEditMode" class="navbtn" ${tieneCUV ? 'disabled style="background-color: #ccc; cursor: not-allowed;" title="No se puede editar: factura tiene CUV asignado"' : ''}> ${tieneCUV ? 'Edición bloqueada' : 'Editar JSON'} </button>
                    <button class="cerrarjson" onclick="cerrarModal()">Cerrar</button>
                </div>
            </div>

            <div id="jsonViewer" style="font-family: monospace; white-space: pre-wrap; background: #f8f8f8; padding: 1rem; border-radius: 8px; max-height: 70vh; overflow: auto;"></div>
            <div id="editModeButtons" style="display: none; padding: 5px; text-align: center; border-top: 1px solid #ddd;">
                <button id="guardarCambios" class="navbtn" style="margin-right: 10px;">Guardar cambios</button>
                <button id="cancelarCambios" class="navbtn" style="background-color: #dc3545;">Cancelar</button>
            </div>
        `;

        overlay.appendChild(modal);
        document.body.appendChild(overlay);


        let overlayClickHandler, modalClickHandler, handleEscape;
        let searchInputHandler, toggleEditHandler, guardarCambiosHandler, cancelarCambiosHandler;
        let prevMatchHandler, nextMatchHandler, searchKeydownHandler;


        let isEditMode = false;
        let originalData = structuredClone(data);
        let currentData = structuredClone(data);


        const jsonViewer = document.getElementById('jsonViewer');
        const toggleEditButton = document.getElementById('toggleEditMode');
        const editModeButtons = document.getElementById('editModeButtons');
        const guardarCambiosBtn = document.getElementById('guardarCambios');
        const cancelarCambiosBtn = document.getElementById('cancelarCambios');
        const searchInput = document.getElementById('jsonSearchInput');
        const matchCounter = document.getElementById('matchCounter');
        const prevMatchButton = document.getElementById('prevMatch');
        const nextMatchButton = document.getElementById('nextMatch');

        let matches = [];
        let currentMatchIndex = -1;
        let originalContent = pretty;

        let collapsedState = new Map();

        function limpiarMemoria() {
            console.log('Iniciando limpieza de memoria...');
            
            if (document.body.contains(overlay)) {
                document.body.removeChild(overlay);
            }
            
            if (overlayClickHandler) overlay.removeEventListener('click', overlayClickHandler);
            if (modalClickHandler) modal.removeEventListener('click', modalClickHandler);
            if (handleEscape) document.removeEventListener('keydown', handleEscape);
            if (searchInputHandler) searchInput.removeEventListener('input', searchInputHandler);
            if (toggleEditHandler) toggleEditButton.removeEventListener('click', toggleEditHandler);
            if (guardarCambiosHandler) guardarCambiosBtn.removeEventListener('click', guardarCambiosHandler);
            if (cancelarCambiosHandler) cancelarCambiosBtn.removeEventListener('click', cancelarCambiosHandler);
            if (prevMatchHandler) prevMatchButton.removeEventListener('click', prevMatchHandler);
            if (nextMatchHandler) nextMatchButton.removeEventListener('click', nextMatchHandler);
            if (searchKeydownHandler) searchInput.removeEventListener('keydown', searchKeydownHandler);

            originalData = null;
            currentData = null;
            originalContent = null;
            matches = null;
            collapsedState.clear();
            collapsedState = null;
            
            jsonViewer.innerHTML = '';
            modal.innerHTML = '';
            
            delete window.cerrarModal;
            delete window.toggleArray;
            
            console.log('Limpieza de memoria completada');
        }

        window.cerrarModal = function() {
            limpiarMemoria();
        };

        overlayClickHandler = function(e) {
            if (e.target === overlay) {
                cerrarModal();
            }
        };

        modalClickHandler = function(e) {
            e.stopPropagation();
        };

        handleEscape = function(e) {
            if (e.key === 'Escape') {
                cerrarModal();
            }
        };

        overlay.addEventListener('click', overlayClickHandler);
        modal.addEventListener('click', modalClickHandler);
        document.addEventListener('keydown', handleEscape);

        function shouldCollapseArray(path) {
            return shouldCollapseArrays; 
        }

        function renderViewJSONWithCollapse(jsonData) {
            return `<div id="jsonContent">${renderCollapsibleJSON(jsonData, '', 0, false)}</div>`;
        }

        function renderEditJSONWithCollapse(jsonData) {
            return `<div id="jsonContent">${renderCollapsibleJSON(jsonData, '', 0, true)}</div>`;
        }

        function renderCollapsibleJSON(obj, path = '', indent = 0, editMode = false) {
            const spacer = '&nbsp;'.repeat(indent * 2);
            let html = '';

            if (Array.isArray(obj)) {
                const arrayId = `array_${path.replace(/\./g, '_')}`;
                const shouldCollapse = shouldCollapseArray(path);
                const isCollapsed = collapsedState.get(arrayId) ?? shouldCollapse;
                
                const toggleText = isCollapsed ? '[+]' : '[-]';
                const itemCount = obj.length;
                
                html += `<button class="collapse-btn" onclick="toggleArray('${arrayId}'); return false;">${toggleText}</button>[`;
                
                if (isCollapsed) {
                    html += `<span class="collapsed-content"> ... ${itemCount} elementos </span>`;
                } else {
                    html += '<br>';
                    obj.forEach((item, index) => {
                        const currentPath = path ? `${path}[${index}]` : `[${index}]`;
                        const comma = index < obj.length - 1 ? ',' : '';
                        html += `<div class="json-line">${spacer}&nbsp;&nbsp;${renderCollapsibleJSON(item, currentPath, indent + 1, editMode)}${comma}</div>`;
                    });
                    html += `${spacer}`;
                }
                
                html += `]`;
                html = `<span id="${arrayId}">${html}</span>`;
                
            } else if (typeof obj === 'object' && obj !== null) {
                html += '{<br>';
                const entries = Object.entries(obj);
                entries.forEach(([key, value], index) => {
                    const currentPath = path ? `${path}.${key}` : key;
                    html += `<div class="json-line">${spacer}&nbsp;&nbsp;"<span class="json-key">${key}</span>": ${renderCollapsibleJSON(value, currentPath, indent + 1, editMode)}${index < entries.length - 1 ? ',' : ''}</div>`;
                });
                html += `${spacer}}`;
            } else {
                if (editMode) {
                    const val = obj !== null ? obj : 'null';
                    
                    const esConsecutivo = path.includes('consecutivo') || path.endsWith('consecutivo');
                    const esNfact = path.includes('numFactura') || path.endsWith('numFactura');
                    const esNumDocumento = path.includes('numDocumentoIdObligado') || path.endsWith('numDocumentoIdObligado');
                    const deshabilitado = esConsecutivo || esNfact || esNumDocumento;

                    const disabledAttr = deshabilitado ? 'disabled' : '';
                    const estiloDeshabilitado = deshabilitado ? 'background-color: #f5f5f5; color: #666; cursor: not-allowed; border: 1px solid #ddd;' : '';
                    const titulo = deshabilitado ? 'Campo no editable' : '';

                    html += `<input class="json-input" 
                                    type="text" 
                                    value="${val}" 
                                    data-path="${path}" 
                                    ${disabledAttr}
                                    style="width: auto; max-width: 200px; display: inline-block; font-size: 12px; font-family: monospace; ${estiloDeshabilitado}" 
                                    title="${titulo}" />`;
                } else {
                    const val = obj !== null ? JSON.stringify(obj) : 'null';
                    html += `<span class="json-value">${val}</span>`;
                }
            }

            return html;
        }

        window.toggleArray = function(arrayId) {
            const isCurrentlyCollapsed = collapsedState.get(arrayId) ?? false;
            collapsedState.set(arrayId, !isCurrentlyCollapsed);
            
            if (isEditMode) {
                updateJSONData();
                jsonViewer.innerHTML = renderEditJSONWithCollapse(currentData);
            } else {
                jsonViewer.innerHTML = renderViewJSONWithCollapse(currentData);
                
                if (searchInput.value.trim()) {
                    searchInputHandler();
                }
            }
        };

        function renderViewJSON(jsonData) {
            if (shouldCollapseArrays) {
                return renderViewJSONWithCollapse(jsonData);
            } else {
                return `<pre id="jsonContent" style="margin: 0; padding: 0;">${JSON.stringify(jsonData, null, 2)}</pre>`;
            }
        }

        function renderEditJSON(jsonData) {
            if (shouldCollapseArrays) {
                return renderEditJSONWithCollapse(jsonData);
            } else {
                return renderEditableJSON(jsonData);
            }
        }

        function renderEditableJSON(obj, path = '', indent = 0) {
            const spacer = '&nbsp;'.repeat(indent * 2);
            let html = '';

            if (Array.isArray(obj)) {
                html += '[<br>';
                obj.forEach((item, index) => {
                    const currentPath = path ? `${path}[${index}]` : `[${index}]`;
                    const comma = index < obj.length - 1 ? ',' : '';
                    html += `<div class="json-line">${spacer}&nbsp;&nbsp;${renderEditableJSON(item, currentPath, indent + 1)}${comma}</div>`;
                });
                html += `${spacer}]`;
            } else if (typeof obj === 'object' && obj !== null) {
                html += '{<br>';
                const entries = Object.entries(obj);
                entries.forEach(([key, value], index) => {
                    const currentPath = path ? `${path}.${key}` : key;
                    html += `<div class="json-line">${spacer}&nbsp;&nbsp;"<span class="json-key">${key}</span>": ${renderEditableJSON(value, currentPath, indent + 1)}${index < entries.length - 1 ? ',' : ''}</div>`;
                });
                html += `${spacer}}`;
            } else {
                const val = obj !== null ? obj : 'null';
                
                const esConsecutivo = path.includes('consecutivo') || path.endsWith('consecutivo');
                const esNfact = path.includes('numFactura') || path.endsWith('numFactura');
                const esNumDocumento = path.includes('numDocumentoIdObligado') || path.endsWith('numDocumentoIdObligado');
                const deshabilitado = esConsecutivo || esNfact || esNumDocumento;

                const disabledAttr = deshabilitado ? 'disabled' : '';
                const estiloDeshabilitado = deshabilitado ? 'background-color: #f5f5f5; color: #666; cursor: not-allowed; border: 1px solid #ddd;' : '';
                const titulo = deshabilitado ? 'Campo no editable' : '';

                html += `<input class="json-input" 
                                type="text" 
                                value="${val}" 
                                data-path="${path}" 
                                ${disabledAttr}
                                style="width: auto; max-width: 200px; display: inline-block; font-size: 12px; font-family: monospace; ${estiloDeshabilitado}" 
                                title="${titulo}" />`;
            }

            return html;
        }

        function toggleEditMode() {
            isEditMode = !isEditMode;
            
            if (isEditMode) {
                originalData = structuredClone(currentData);
                toggleEditButton.textContent = 'Ver JSON';
                toggleEditButton.style.backgroundColor = '#28a745';
                editModeButtons.style.display = 'block';
                searchInput.disabled = true;
                searchInput.style.opacity = '0.5';
                prevMatchButton.style.display = 'none';
                nextMatchButton.style.display = 'none';
                matchCounter.textContent = 'Modo edición';
                jsonViewer.style.padding = '1rem';
                jsonViewer.innerHTML = renderEditJSON(currentData);
            } else {
                toggleEditButton.textContent = 'Editar JSON';
                toggleEditButton.style.backgroundColor = '#9b87f5';
                editModeButtons.style.display = 'none';
                searchInput.disabled = false;
                searchInput.style.opacity = '1';
                matchCounter.textContent = '0 de 0';
                jsonViewer.style.padding = '1rem';
                jsonViewer.innerHTML = renderViewJSON(currentData);
                originalContent = JSON.stringify(currentData, null, 2);
                searchInput.value = '';
                matches = [];
                currentMatchIndex = -1;
            }
        }

        function updateJSONData() {
            const inputs = jsonViewer.querySelectorAll('.json-input');
            inputs.forEach(input => {
                const path = input.dataset.path;
                const raw = input.value.trim();

                let value;
                if (raw.toLowerCase() === 'null') {
                    value = null;
                } else if (raw === '') {
                    value = '';
                } else if (/^-?\d+(\.\d+)?$/.test(raw) && !/^0\d/.test(raw)) {
                    value = Number(raw);
                } else {
                    value = raw;
                }

                setNestedValue(currentData, path, value);
            });
        }

        function setNestedValue(obj, path, value) {
            const keys = path.split(/[\.\[\]]+/).filter(k => k);
            let current = obj;
            
            for (let i = 0; i < keys.length - 1; i++) {
                const key = keys[i];
                if (!(key in current)) {
                    current[key] = {};
                }
                current = current[key];
            }
            
            const lastKey = keys[keys.length - 1];
            current[lastKey] = value;
        }

        function obtenerCambios(original, actual, path = '') {
            let cambios = {};
            const allKeys = new Set([
                ...Object.keys(original || {}),
                ...Object.keys(actual || {})
            ]);

            for (const key of allKeys) {
                const fullPath = path ? `${path}.${key}` : key;
                const originalValue = original ? original[key] : undefined;
                const actualValue = actual ? actual[key] : undefined;

                if (typeof actualValue === 'object' && actualValue !== null && !Array.isArray(actualValue) &&
                    typeof originalValue === 'object' && originalValue !== null && !Array.isArray(originalValue)) {
                    
                    const nestedChanges = obtenerCambios(originalValue, actualValue, fullPath);
                    if (Object.keys(nestedChanges).length > 0) {
                        Object.assign(cambios, nestedChanges);
                    }
                } else if (actualValue !== originalValue) {
                    cambios[fullPath] = actualValue;
                }
            }

            return cambios;
        }

        function getDiffUsuarios(originalUsuarios, editedUsuarios) {
            const diffUsuarios = [];

            for (const edited of editedUsuarios) {
                const original = originalUsuarios.find(o => o.consecutivo === edited.consecutivo);
                let tieneChangesCamposPrincipales = false;
                let tieneChangesServicios = false;
                const serviciosDiff = {};

                for (const key in edited) {
                    if (key === "servicios" || key === "consecutivo") continue;
                    if (!original || edited[key] != original[key]) {
                        tieneChangesCamposPrincipales = true;
                        break;
                    }
                }

                if (edited.servicios && original?.servicios) {
                    for (const tipo in edited.servicios) {
                        const editedList = edited.servicios[tipo] || [];
                        const originalList = original.servicios[tipo] || [];

                        const cambiosTipo = [];

                        for (const item of editedList) {
                            const origItem = originalList.find(o => o.consecutivo === item.consecutivo);
                            let tieneChangesEnItem = false;
                            
                            for (const key in item) {
                                if (!origItem || item[key] != origItem[key]) {
                                    tieneChangesEnItem = true;
                                    break;
                                }
                            }
                            
                            if (tieneChangesEnItem) {
                                cambiosTipo.push(item); 
                            }
                        }

                        if (cambiosTipo.length > 0) {
                            serviciosDiff[tipo] = cambiosTipo;
                            tieneChangesServicios = true;
                        }
                    }
                }

                if (tieneChangesCamposPrincipales || tieneChangesServicios) {
                    const usuarioCompleto = {
                        consecutivo: edited.consecutivo,
                        tipoDocumentoIdentificacion: edited.tipoDocumentoIdentificacion,
                        numDocumentoIdentificacion: edited.numDocumentoIdentificacion,
                        tipoUsuario: edited.tipoUsuario,
                        fechaNacimiento: edited.fechaNacimiento,
                        codSexo: edited.codSexo,
                        codPaisResidencia: edited.codPaisResidencia,
                        codMunicipioResidencia: edited.codMunicipioResidencia,
                        codZonaTerritorialResidencia: edited.codZonaTerritorialResidencia,
                        incapacidad: edited.incapacidad,
                        codPaisOrigen: edited.codPaisOrigen
                    };

                    if (tieneChangesServicios) {
                        usuarioCompleto.servicios = serviciosDiff;
                    }

                    diffUsuarios.push(usuarioCompleto);
                }
            }

            return diffUsuarios;
        }

        toggleEditHandler = toggleEditMode;

        guardarCambiosHandler = async () => {
            if (tieneCUV) {
                showToast('Advertencia', `No se puede editar el JSON: la factura tiene CUV asignado (${cuvValue})`, 'warning');
                return;
            }

            if (!tieneCUV) {
                const idEstadoValidacion = 2;
                actualizarSoloEstado(nFact, idEstadoValidacion);
            }

            updateJSONData();

            const cambios = {};

            if (currentData.numFactura != originalData.numFactura) {
                cambios.numFactura = currentData.numFactura;
            }
            if (currentData.numDocumentoIdObligado != originalData.numDocumentoIdObligado) {
                cambios.numDocumentoIdObligado = currentData.numDocumentoIdObligado;
            }
            if (currentData.tipoNota != originalData.tipoNota) {
                cambios.tipoNota = currentData.tipoNota;
            }
            if (currentData.numNota != originalData.numNota) {
                cambios.numNota = currentData.numNota;
            }

            const usuariosDiff = getDiffUsuarios(originalData.usuarios, currentData.usuarios);
            if (usuariosDiff.length > 0) {
                cambios.usuarios = usuariosDiff;
            }

            if (Object.keys(cambios).length === 0) {
                showToast('Info', 'No se detectaron cambios', 'info');
                return;
            }

            console.log('===== ENVIANDO SOLO CAMBIOS AL BACKEND =====');
            console.log(JSON.stringify(cambios, null, 2));
            console.log('Tamaño:', JSON.stringify(cambios).length, 'caracteres');
            console.log('===============================================');

            try {
                const host = window.location.hostname;
                const url = `https://${host}:9876/api/sql/actualizarCampos?idMovDoc=${idMovDoc}`;

                const fetchOptions = {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json'
                    },
                    body: JSON.stringify(cambios)
                };

                const response = await fetch(url, fetchOptions);

                if (response.ok) {
                    const responseText = await response.text();
                    showToast('Éxito', 'Cambios guardados correctamente', 'success');
                    originalData = structuredClone(currentData);
                    toggleEditMode();
                } else {
                    const errorText = await response.text();
                    showToast('Error', `Error al guardar cambios: ${errorText}`, 'error');
                }
            } catch (e) {
                console.error("⚠ Error en fetch:", e);
                showToast('Error', `Fallo la petición: ${e.message}`, 'error');
            }
        };


        cancelarCambiosHandler = () => {
            currentData = structuredClone(originalData);
            toggleEditMode();
            showToast('Info', 'Cambios cancelados', 'info');
        };

        jsonViewer.innerHTML = renderViewJSON(currentData);

        searchInputHandler = () => {
            if (isEditMode) return;
            
            const searchTerm = searchInput.value.trim().toLowerCase();
            const jsonContent = document.getElementById('jsonContent');
            
            matches.length = 0;
            currentMatchIndex = -1;
            
            if (!searchTerm) {
                jsonViewer.innerHTML = renderViewJSON(currentData);
                updateMatchCounter();
                return;
            }
            
            const searchContent = shouldCollapseArrays ? 
                JSON.stringify(currentData, null, 2) : 
                jsonContent.textContent;
                
            const lines = searchContent.split('\n');
            const highlightedLines = [];
            
            lines.forEach((line, index) => {
                if (line.toLowerCase().includes(searchTerm)) {
                    matches.push(index);
                    const highlightedLine = line.replace(
                        new RegExp(`(${escapeRegExp(searchTerm)})`, 'gi'), 
                        '<mark class="match" data-line="' + index + '">$1</mark>'
                    );
                    highlightedLines.push(highlightedLine);
                } else {
                    highlightedLines.push(line);
                }
            });
            
            if (shouldCollapseArrays) {
                jsonContent.innerHTML = `<pre style="margin: 0;">${highlightedLines.join('\n')}</pre>`;
            } else {
                jsonContent.innerHTML = highlightedLines.join('\n');
            }
            
            updateMatchCounter();
        };

        function escapeRegExp(string) {
            return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        }

        const updateMatchCounter = () => {
            if (matches.length > 0) {
                currentMatchIndex = 0;
                highlightCurrentMatch();
                matchCounter.textContent = `${currentMatchIndex + 1} de ${matches.length}`;
                prevMatchButton.style.display = 'inline';
                nextMatchButton.style.display = 'inline';
            } else {
                matchCounter.textContent = searchInput.value.trim() ? '0 de 0' : '0 de 0';
                prevMatchButton.style.display = 'none';
                nextMatchButton.style.display = 'none';
                const jsonContent = document.getElementById('jsonContent');
                if (jsonContent) {
                    const currentHighlight = jsonContent.querySelector('.current-match');
                    if (currentHighlight) {
                        currentHighlight.classList.remove('current-match');
                    }
                }
            }
        };

        const highlightCurrentMatch = () => {
            const jsonContent = document.getElementById('jsonContent');
            if (!jsonContent) return;
            
            const previousCurrent = jsonContent.querySelector('.current-match');
            if (previousCurrent) {
                previousCurrent.classList.remove('current-match');
            }
            
            const allMatches = jsonContent.querySelectorAll('.match');
            const matchesInCurrentLine = Array.from(allMatches).filter(
                match => parseInt(match.dataset.line) === matches[currentMatchIndex]
            );
            
            if (matchesInCurrentLine.length > 0) {
                matchesInCurrentLine[0].classList.add('current-match');
                matchesInCurrentLine[0].scrollIntoView({
                    behavior: 'smooth',
                    block: 'center'
                });
            }
        };

        prevMatchHandler = () => {
            if (matches.length > 0 && currentMatchIndex > 0) {
                currentMatchIndex--;
                highlightCurrentMatch();
                matchCounter.textContent = `${currentMatchIndex + 1} de ${matches.length}`;
            }
        };

        nextMatchHandler = () => {
            if (matches.length > 0 && currentMatchIndex < matches.length - 1) {
                currentMatchIndex++;
                highlightCurrentMatch();
                matchCounter.textContent = `${currentMatchIndex + 1} de ${matches.length}`;
            }
        };

        searchKeydownHandler = (e) => {
            if (e.key === 'Enter' && !isEditMode) {
                e.preventDefault();
                if (e.shiftKey) {
                    prevMatchHandler();
                } else {
                    nextMatchHandler();
                }
            }
        };

        searchInput.addEventListener('input', searchInputHandler);
        toggleEditButton.addEventListener('click', toggleEditHandler);
        guardarCambiosBtn.addEventListener('click', guardarCambiosHandler);
        cancelarCambiosBtn.addEventListener('click', cancelarCambiosHandler);
        prevMatchButton.addEventListener('click', prevMatchHandler);
        nextMatchButton.addEventListener('click', nextMatchHandler);
        searchInput.addEventListener('keydown', searchKeydownHandler);

    } catch (error) {
        console.error('Error:', error);
        showToast('Error', `Error al procesar el JSON: ${error.message}`, 'error');
    } finally {
        if (button) {
            button.disabled = false;
            button.innerHTML = originalHTML;
            button.style.opacity = '1';
            button.style.cursor = 'pointer';
        }
    }
}




// ==========================================================
// FUNCIONES PARA ENVIO DE PAQUETES
// ==========================================================

/**
 * Envía múltiples paquetes de facturas al validador del Ministerio de Salud.
 * Procesa las facturas seleccionadas mediante checkboxes, ejecuta RIPS, genera JSON y XML,
 * y envía cada paquete al servidor de validación.
 * 
 * @async
 * @function enviarPaquetes
 * @returns {Promise<void>}
 * 
 * @throws {Error} Si falla la comunicación con el servidor o el procesamiento de facturas
 * 
 */

async function enviarPaquetes() {
    const boton = document.getElementById('enviarPaquetesBtn');
    const textoOriginal = boton.innerHTML;
    const boton2 = document.getElementById('botonBuscar');

    const boton3 = document.getElementById('DescargarPaquetesBtn')

    boton.disabled = true;
    boton.innerHTML = 'Enviando...';
    boton.style.opacity = '0.6';
    boton.style.cursor = 'not-allowed';

    boton2.disabled = true;
    boton2.style.opacity = '0.6';
    boton2.style.cursor = 'not-allowed';

    boton3.disabled = true;
    boton3.style.opacity = '0.6';
    boton3.style.cursor = 'not-allowed';



    try {
        const checkboxes = document.querySelectorAll('.filaCheckbox:checked');

        if (checkboxes.length === 0) {
            showToast('Error', 'No hay facturas seleccionadas', 'error');
            return;
        }

        toast = showToast('Procesando', `Enviando ${checkboxes.length} paquetes...`, 'success', 30000, true);
        actualizarToastProgreso(toast, 0)

        const documentosSeleccionados = Array.from(checkboxes).map(cb => {
            const fila = cb.closest('tr');
            const idMovDoc = cb.value;
            return { idMovDoc, fila };
        });

        const host = window.location.hostname;
        const token = sessionStorage.getItem('token'); 


        for (const doc of documentosSeleccionados) {
            const resultadoDiv = doc.fila.querySelector('td:nth-last-child(5)');
            const observacionesTd = doc.fila.querySelector('td:nth-last-child(4)');
            const nFact = doc.fila.querySelector('td:nth-child(2)').textContent;
            const logsButton = doc.fila.querySelector('.descargar-logs-btn');
            const cuvTd = doc.fila.querySelector('td:nth-last-child(2)');
            const cuvValor = cuvTd?.textContent?.trim();

            if (cuvValor && cuvValor.length > 0) {
                showToast('Advertencia', `La factura ${nFact} ya tiene un CUV registrado. No será procesada.`, 'warning');
                continue; 
            }
            
            try {
                const ejecutarRipsUrl = `https://${host}:9876/api/sql/ejecutarRips?Nfact=${nFact}`;
                const ejecutarRipsResponse = await fetch(ejecutarRipsUrl);
                if (!ejecutarRipsResponse.ok) {
                    const errorText = await ejecutarRipsResponse.text();
                    resultadoDiv.textContent = `Error al ejecutar Rips: ${errorText}`;
                    showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
                    doc.fila.classList.add('error-row');
                    continue;
                }

                const jsonResponse = await fetch(`https://${host}:9876/facturas/generarjson/${doc.idMovDoc}`);
                if (!jsonResponse.ok) {
                    const errorText = await jsonResponse.text();
                    resultadoDiv.textContent = `Error al obtener JSON: ${errorText}`;
                    showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
                    doc.fila.classList.add('error-row');
                    continue;
                }

                const jsonData = await jsonResponse.json();

                const xmlResponse = await fetch(`https://${host}:9876/api/validador/base64/${doc.idMovDoc}`);
                if (!xmlResponse.ok) {
                    const errorText = await xmlResponse.text();
                    resultadoDiv.textContent = `Error al obtener XML: ${errorText}`;
                    showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
                    doc.fila.classList.add('error-row');
                    continue;
                }

                const xmlText = await xmlResponse.text();

                const progreso = Math.round(((documentosSeleccionados.indexOf(doc) + 1) / documentosSeleccionados.length) * 100);
                actualizarToastProgreso(toast, progreso);
                
                const payload = {
                    rips: jsonData,
                    xmlFevFile: xmlText
                };

                const response = await fetch(`https://${host}:9876/api/validador/subir?nFact=${encodeURIComponent(nFact)}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    body: JSON.stringify(payload)
                });

                const responseData = await response.json();



                if (response.ok && responseData.ResultState === true) {
        
                    const cuv = encodeURIComponent(responseData.CodigoUnicoValidacion || '');
                    
                    if (cuv) {

                        if (logsButton) {
                            logsButton.style.display = 'none';
                            delete logsButton.dataset.downloadUrl;
                            delete logsButton.dataset.filename;
                        }

                        const txtContent = JSON.stringify(responseData, null, 2);
                        
                        await fetch(`https://${host}:9876/api/validador/guardarrespuesta`, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                            },
                            body: JSON.stringify({
                                nFact: nFact,
                                mensajeRespuesta: txtContent
                            })
                        });


                        const idEstadoValidacion = 3;
                        const exitoso = await agregarCUVCompleto(nFact, cuv, idEstadoValidacion);
                        if (exitoso) {
                            await actualizarCUVEnFila(doc.fila, nFact);
                            showToast('Éxito', `CUV agregado para NFact ${nFact}`, 'success');
                        } else {
                            showToast('Advertencia', 'Paquete fue exitoso pero no se guardó el CUV', 'warning');
                        }
                    } else {
                        console.warn(`Respuesta exitosa sin CUV para NFact ${nFact}`);
                        showToast('Advertencia', `Paquete exitoso pero sin CUV para NFact ${nFact}`, 'warning');
                    }


                } else {
                    const errores = responseData.ResultadosValidacion || [];
                    let cuvAgregado = false;

                    const errorRVG18 = errores.find(error => error.Codigo === 'RVG18');
                    if (errorRVG18 && errorRVG18.Observaciones) {
                        const ripsCuv = encodeURIComponent(errorRVG18.Observaciones.trim());
                        const url = `https://${host}:9876/api/sql/agregarcuv?nFact=${nFact}&ripsCuv=${ripsCuv}`;

                        const idEstadoValidacion = 3;

                        const exitoso = await agregarCUVCompleto(nFact, ripsCuv, idEstadoValidacion);
                        if (exitoso) {
                            console.log(`CUV agregado para NFact ${nFact}`);
                            await actualizarCUVEnFila(doc.fila, nFact);
                            showToast('Éxito', `CUV agregado para NFact ${nFact}`, 'success');
                            cuvAgregado = true;

                            if (logsButton) {
                                logsButton.style.display = 'none';
                                delete logsButton.dataset.downloadUrl;
                                delete logsButton.dataset.filename;
                            }

                            const txtContent = JSON.stringify(responseData, null, 2);
                            const blob = new Blob([txtContent], { type: 'text/plain' });
                            const downloadUrl = URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = downloadUrl;
                            a.download = `ResultadosMSPS_${nFact}_A_CUV.txt`;
                            document.body.appendChild(a);
                            a.click();
                            a.remove();
                            URL.revokeObjectURL(downloadUrl);
                        }
                    }

                    if (!cuvAgregado) {

                        const logsButton = doc.fila.querySelector('.descargar-logs-btn');


                        if (logsButton) {
                            logsButton.style.display = 'inline-block';

                            const blob = new Blob([JSON.stringify(responseData, null, 2)], { type: 'text/plain' });
                            const url = URL.createObjectURL(blob);

                            logsButton.dataset.downloadUrl = url;
                            logsButton.dataset.filename = `LogsMSPS_${nFact}.txt`;
                        }
        
                        const estadoActual = await obtenerEstadoValidacion(nFact);

                        if (estadoActual === null) {
                            console.warn(`No se pudo obtener el estado de validación para ${nFact}`);
                            return;
                        }

                        let idEstadoValidacion;

                        if (estadoActual === 2) {
                            idEstadoValidacion = 2;
                        } else {
                            idEstadoValidacion = 4;
                        }
                        const exitoso = await actualizarSoloEstado(nFact, idEstadoValidacion);

                        if (responseData.error) {
                            const mensajeError = responseData.error;

                            resultadoDiv.textContent = mensajeError;

                            showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
                        }

                        else if (responseData.ResultadosValidacion && Array.isArray(responseData.ResultadosValidacion)) {

                            const errores = responseData.ResultadosValidacion;

                            const mensajes = errores.map(error =>
                                `${error.Clase} \n ${error.Codigo} \n ${error.Descripcion}`
                            ).join('\n\n');

                            const observacionesYFuente = errores.map(error =>
                                `${error.Observaciones} \n  ${error.PathFuente} \n Fuente: ${error.Fuente}`
                            ).join('\n\n');

                            if (mensajes.length > 100) {
                                resultadoDiv.innerHTML = `
                                    <div class="expandable">
                                        <button class="toggle-button" onclick="toggleExpand(this)">
                                            <span class="arrow">▶</span>
                                        </button>
                                        <div class="expand-content">
                                            <ol class="lista-enumerada">
                                                ${mensajes.split('\n\n').map(item => `<li>${item.replace(/\n/g, '<br>')}</li>`).join('')}
                                            </ol>
                                        </div>
                                    </div>`;
                            } else {
                                resultadoDiv.textContent = mensajes;
                            }

                            if (observacionesYFuente.length > 100) {
                                observacionesTd.innerHTML = `
                                    <div class="expandable">
                                        <button class="toggle-button" onclick="toggleExpand(this)">
                                            <span class="arrow">▶</span>
                                        </button>
                                        <div class="expand-content">
                                            <ol class="lista-enumerada">
                                                ${observacionesYFuente.split('\n\n').map(item => `<li>${item.replace(/\n/g, '<br>')}</li>`).join('')}
                                            </ol>
                                        </div>
                                    </div>`;
                            } else {
                                observacionesTd.textContent = observacionesYFuente;
                            }

                            showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');

                        } else {

                            resultadoDiv.textContent = 'Error desconocido - revisar logs';
                            observacionesTd.textContent = 'Estructura de respuesta no reconocida';
                            showToast('Error', `El paquete ${nFact} tuvo un error desconocido`, 'error');
                        }

                            doc.fila.classList.add('error-row');
                    }
                }
            } catch (error) {
                console.error("Error en enviarPaquetes:", error);
                resultadoDiv.textContent = 'El token es incorrecto o está vacío';
                doc.fila.classList.add('error-row');
            }
        }

        

        checkboxes.forEach(cb => cb.checked = false);
        document.getElementById('selectAll').checked = false;
        verificarCheckboxes();

        showToast('Finalizado', `${documentosSeleccionados.length} paquetes procesados`, 'success');
    } catch (error) {
        console.error("Error general al enviar paquetes:", error);
        showToast('Error', `No se pudieron enviar los paquetes: ${error.message}`, 'error');
    } finally {
        boton.innerHTML = textoOriginal;
        boton.disabled = true;
        boton.style.opacity = '1';
        boton.style.cursor = 'pointer';

        boton2.disabled = false;
        boton2.style.opacity = '1';
        boton2.style.cursor = 'pointer';

        boton3.disabled = true;
        boton3.style.opacity = '1';
        boton3.style.cursor = 'pointer';

        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);
    }
}

/**
 * Envía un paquete individual de factura al validador del Ministerio de Salud.
 * 
 * @async
 * @function enviarPaquete
 * @param {string} idMovDoc - ID del documento de movimiento (factura) a procesar
 * @param {HTMLButtonElement} buttonElement - Elemento del botón que inició el envío
 * @returns {Promise<void>}
 * 
 * 
 * @throws {Error} Si falla la autenticación (token inválido/vacío)
 * @throws {Error} Si falla la comunicación con el servidor
 * @throws {Error} Si el procesamiento de RIPS, JSON o XML falla
 */
async function enviarPaquete(idMovDoc, buttonElement) {
    const host = window.location.hostname;
    const token = sessionStorage.getItem('token');
    const botonBuscar = document.getElementById("botonBuscar");

    const originalHTML = buttonElement.innerHTML;
    buttonElement.disabled = true;
    buttonElement.innerHTML = 'Enviando...';
    buttonElement.style.opacity = '0.6';
    buttonElement.style.cursor = 'not-allowed';

    botonBuscar.disabled = true;
    botonBuscar.style.opacity = '0.6';
    botonBuscar.style.cursor = 'not-allowed';

    
    const fila = buttonElement.closest('tr');
    const resultadoDiv = fila.querySelector('td:nth-last-child(5)');
    const observacionesTd = fila.querySelector('td:nth-last-child(4)');
    const descripcionTd = fila.querySelector('td:nth-last-child(6)');
    const nFact = fila.querySelector('td:nth-child(2)').textContent;
    const logsButton = fila.querySelector('.descargar-logs-btn');

    try {

        const toast = showToast('Procesando', `Preparando paquete...`, 'success', 30000, true);
        actualizarToastProgreso(toast, 0);

        const ejecutarRipsUrl = await fetch(`https://${host}:9876/api/sql/ejecutarRips?Nfact=${nFact}`);
        if (!ejecutarRipsUrl.ok) {
            const errorText = await ejecutarRipsUrl.text();
            resultadoDiv.textContent = `Error al ejecutar Rips: ${errorText}`;
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
            fila.classList.add('error-row');
            return;
        }

        actualizarToastProgreso(toast, 33);

        const jsonResponse = await fetch(`https://${host}:9876/facturas/generarjson/${idMovDoc}`);
        if (!jsonResponse.ok) {
            const errorText = await jsonResponse.text();
            resultadoDiv.textContent = `Error al obtener JSON: ${errorText}`;
            fila.classList.add('error-row');
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
            return;
        }

        actualizarToastProgreso(toast, 66);

        const jsonData = await jsonResponse.json();

        const xmlResponse = await fetch(`https://${host}:9876/api/validador/base64/${idMovDoc}`);
        if (!xmlResponse.ok) {
            const errorText = await xmlResponse.text();
            resultadoDiv.textContent = `Error al obtener XML: ${errorText}`;
            fila.classList.add('error-row');
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
            return;
        }

        actualizarToastProgreso(toast, 100);
        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);

        showToast('Procesando', `Enviando paquete...`, 'success');

        const xmlText = await xmlResponse.text();

        const payload = {
            rips: jsonData,
            xmlFevFile: xmlText
        };

        const response = await fetch(`https://${host}:9876/api/validador/subir?nFact=${encodeURIComponent(nFact)}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify(payload)
        });

        const responseData = await response.json();

        if (response.ok && responseData.ResultState === true) {
            
            const cuv = encodeURIComponent(responseData.CodigoUnicoValidacion || '');
            
            if (cuv) {

                if (logsButton) {
                    logsButton.style.display = 'none';
                    delete logsButton.dataset.downloadUrl;
                    delete logsButton.dataset.filename;
                }

                const txtContent = JSON.stringify(responseData, null, 2);
                
                await fetch(`https://${host}:9876/api/validador/guardarrespuesta`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        nFact: nFact,
                        mensajeRespuesta: txtContent
                    })
                });
                
                const idEstadoValidacion = 3;
                const exitoso = await agregarCUVCompleto(nFact, cuv, idEstadoValidacion);
                if (exitoso) {
                    await actualizarCUVEnFila(fila, nFact);
                    showToast('Éxito', `CUV agregado para NFact ${nFact}`, 'success');
                } else {
                    showToast('Advertencia', 'Paquete fue exitoso pero no se guardó el CUV', 'warning');
                }
            } else {
                console.warn(`Respuesta exitosa sin CUV para NFact ${nFact}`);
                showToast('Advertencia', `Paquete exitoso pero sin CUV para NFact ${nFact}`, 'warning');

            }

        } else {
            const errores = responseData.ResultadosValidacion || [];
            let cuvAgregado = false;

            const errorRVG18 = errores.find(error => error.Codigo === 'RVG18');
            if (errorRVG18 && errorRVG18.Observaciones) {
                const ripsCuv = encodeURIComponent(errorRVG18.Observaciones.trim());
                
                const idEstadoValidacion = 3;
                
                const exitoso = await agregarCUVCompleto(nFact, ripsCuv, idEstadoValidacion);
                if (exitoso) {
                    console.log(`CUV agregado para NFact ${nFact}`);
                    await actualizarCUVEnFila(fila, nFact);
                    showToast('Éxito', `CUV agregado para NFact ${nFact}`, 'success');
                    cuvAgregado = true;

                    if (logsButton) {
                        logsButton.style.display = 'none';
                        delete logsButton.dataset.downloadUrl;
                        delete logsButton.dataset.filename;
                    }

                    const txtContent = JSON.stringify(responseData, null, 2);
                    const blob = new Blob([txtContent], { type: 'text/plain' });
                    const downloadUrl = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = downloadUrl;
                    a.download = `ResultadosMSPS_${nFact}_A_CUV.txt`;
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    URL.revokeObjectURL(downloadUrl);
                }
            }

            if (!cuvAgregado) {

                if (logsButton) {
                    logsButton.style.display = 'inline-block';

                    const blob = new Blob([JSON.stringify(responseData, null, 2)], { type: 'text/plain' });
                    const url = URL.createObjectURL(blob);

                    logsButton.dataset.downloadUrl = url;
                    logsButton.dataset.filename = `LogsMSPS_${nFact}.txt`;
                }

                const estadoActual = await obtenerEstadoValidacion(nFact);

                if (estadoActual === null) {
                    console.warn(`No se pudo obtener el estado de validación para ${nFact}`);
                    return;
                }

                const idEstadoValidacion = estadoActual === 2 ? 2 : 4;

                const exitoso = await actualizarSoloEstado(nFact, idEstadoValidacion);
                
                if (responseData.error) {
                    const mensajeError = responseData.error;
                    
                    resultadoDiv.textContent = mensajeError;
                    
                    showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');
                    
                } else if (responseData.ResultadosValidacion && Array.isArray(responseData.ResultadosValidacion)) {
                    const errores = responseData.ResultadosValidacion;
                    
                    const mensajes = errores.map(error =>
                        `${error.Clase} \n ${error.Codigo} \n ${error.Descripcion}`
                    ).join('\n\n');

                    const observacionesYFuente = errores.map(error =>
                        `${error.Observaciones} \n  ${error.PathFuente} \n Fuente: ${error.Fuente}`
                    ).join('\n\n');

                    if (mensajes.length > 100) {
                        resultadoDiv.innerHTML = `
                            <div class="expandable">
                                <button class="toggle-button" onclick="toggleExpand(this)">
                                    <span class="arrow">▶</span>
                                </button>
                                <div class="expand-content">
                                    <ol class="lista-enumerada">
                                        ${mensajes.split('\n\n').map(item => `<li>${item.replace(/\n/g, '<br>')}</li>`).join('')}
                                    </ol>
                                </div>
                            </div>`;
                    } else {
                        resultadoDiv.textContent = mensajes;
                    }

                    if (observacionesYFuente.length > 100) {
                        observacionesTd.innerHTML = `
                            <div class="expandable">
                                <button class="toggle-button" onclick="toggleExpand(this)">
                                    <span class="arrow">▶</span>
                                </button>
                                <div class="expand-content">
                                    <ol class="lista-enumerada">
                                        ${observacionesYFuente.split('\n\n').map(item => `<li>${item.replace(/\n/g, '<br>')}</li>`).join('')}
                                    </ol>
                                </div>
                            </div>`;
                    } else {
                        observacionesTd.textContent = observacionesYFuente;
                    }

                    showToast('Error', `El paquete ${nFact} fue rechazado`, 'error');

            } else {
                    resultadoDiv.textContent = 'Error desconocido - revisar logs';
                    observacionesTd.textContent = 'Estructura de respuesta no reconocida';
                    showToast('Error', `El paquete ${nFact} tuvo un error desconocido`, 'error');
                }

                fila.classList.add('error-row');
            }
        }

    } catch (error) {
        console.error('Error al enviar paquete:', error);
        showToast('Error', `El token es incorrecto o está vacio`, 'error')
        resultadoDiv.textContent = 'El token es incorrecto o está vacío';
        fila.classList.add('error-row');
    } finally {
        buttonElement.disabled = false;
        buttonElement.innerHTML = originalHTML;
        buttonElement.style.opacity = '1';
        buttonElement.style.cursor = 'pointer';

        botonBuscar.disabled = false;
        botonBuscar.style.opacity = '1';
        botonBuscar.style.cursor = 'pointer';
    }
}


// ==========================================================
// FUNCIONES PARA DESCARGAS 
// ==========================================================

/**
 * Descarga múltiples paquetes de facturas seleccionadas mediante checkboxes.
 * Ejecuta RIPS, descarga archivos ZIP y genera un reporte detallado del proceso.
 * 
 * @async
 * @function DescargarPaquetes
 * @returns {Promise<void>}
 * 
 */
async function DescargarPaquetes() {
    const checkboxes = document.querySelectorAll('.filaCheckbox:checked');

    if (checkboxes.length === 0) {
        showToast('Error', 'No hay paquetes seleccionados para descargar', 'error');
        return;
    }

    const botonBuscar = document.getElementById("botonBuscar");
    const botonEnviar = document.getElementById("enviarPaquetesBtn");
    const downloadButton = document.querySelector('#DescargarPaquetesBtn');

    let originalHTML = '';

    if (downloadButton) {
        originalHTML = downloadButton.innerHTML;
        downloadButton.disabled = true;
        downloadButton.innerHTML = 'Descargando...';
        downloadButton.style.opacity = '0.6';
        downloadButton.style.cursor = 'not-allowed';
    }

    if (botonBuscar) {
        botonBuscar.disabled = true;
        botonBuscar.style.opacity = '0.6';
        botonBuscar.style.cursor = 'not-allowed';
    }

    if (botonEnviar) {
        botonEnviar.disabled = true;
        botonEnviar.style.opacity = '0.6';
        botonEnviar.style.cursor = 'not-allowed';
    }

    let directoryHandle;
    try {
        try {
            directoryHandle = await window.showDirectoryPicker();

            const permiso = await directoryHandle.requestPermission({ mode: 'readwrite' });
            if (permiso !== 'granted') {
                showToast('Error', 'Permiso denegado para escribir en la carpeta seleccionada.', 'error');
                return;
            }

        } catch (e) {
            showToast('Cancelado', 'Selección de carpeta cancelada.', 'error');
            return;
        }

        const toast = showToast('Procesando', `Descargando ${checkboxes.length} paquetes...`, 'success', 60000, true);
        actualizarToastProgreso(toast, 0);

        const documentosSeleccionados = Array.from(checkboxes).map(cb => {
            const fila = cb.closest('tr');
            const idMovDoc = cb.value;
            const nFact = fila.querySelector('td:nth-child(2)').textContent.trim();
            return { idMovDoc, nFact, fila};
        });


        const reporte = [];
        const fechaHora = new Date().toLocaleString('es-CO', { 
            year: 'numeric', 
            month: '2-digit', 
            day: '2-digit',
            hour: '2-digit', 
            minute: '2-digit', 
            second: '2-digit' 
        });

        reporte.push('='.repeat(80));
        reporte.push(`REPORTE DE DESCARGA DE PAQUETES`);
        reporte.push(`Fecha: ${fechaHora}`);
        reporte.push(`Total de facturas seleccionadas: ${documentosSeleccionados.length}`);
        reporte.push('='.repeat(80));
        reporte.push('');

        // Fase 1: Ejecucion Procedimiento Almacenado
        reporte.push('--- FASE 1: Ejecutar Rips ---');
        reporte.push('');
        
        const erroresRips = [];
        for (let i = 0; i < documentosSeleccionados.length; i++) {
            const doc = documentosSeleccionados[i];
            const ripsError = await ejecutarRips(doc.nFact);
            
            if (ripsError) {
                erroresRips.push(doc.idMovDoc);
                reporte.push(`⚠ ${doc.nFact}: ERROR RIPS`);
                reporte.push(`   Detalle: ${ripsError}`);
                reporte.push('');
            }

            // Actualizar progreso 
            const progresoRips = Math.round(((i + 1) / documentosSeleccionados.length) * 30);
            actualizarToastProgreso(toast, progresoRips);
        }

        const documentosValidos = documentosSeleccionados.filter(d => !erroresRips.includes(d.idMovDoc));
        
        reporte.push('');
        reporte.push(`Resumen RIPS: ${documentosValidos.length} exitosos, ${erroresRips.length} fallidos`);
        reporte.push('='.repeat(80));
        reporte.push('');

        if (documentosValidos.length === 0) {
            reporte.push('⚠ PROCESO FINALIZADO: Ningún documento pasó la validación RIPS');
            await guardarReporte(directoryHandle, reporte);
            
            showToast('Error', 'Ningún documento pasó validación RIPS. Se generó reporte con detalles.', 'error', 8000);
            
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            return;
        }

        // Fase 2: Descarga de ZIPs
        reporte.push('--- FASE 2: DESCARGA DE PAQUETES ---');
        reporte.push('');

        let paquetesExitosos = [];
        let paquetesFallidos = [];
        const incluirXml = document.getElementById('incluirXML').checked;

        for (let i = 0; i < documentosValidos.length; i++) {
            const doc = documentosValidos[i];
            const downloadError = await descargarZip(doc.idMovDoc, "json", incluirXml, directoryHandle);
            
            if (downloadError) {
                paquetesFallidos.push(doc.nFact);
                reporte.push(`⚠ ${doc.nFact}: ERROR EN DESCARGA`);
                reporte.push(`   Detalle: ${downloadError}`);
                reporte.push('');
            } else {
                paquetesExitosos.push(doc.nFact);
                reporte.push(`✓ ${doc.nFact}: Descarga exitosa`);
            }

            // Actualizar progreso (fase descarga = 30-100%)
            const progresoDescarga = 30 + Math.round(((i + 1) / documentosValidos.length) * 70);
            actualizarToastProgreso(toast, progresoDescarga);
        }

        // Resumen final
        reporte.push('');
        reporte.push('='.repeat(80));
        reporte.push('RESUMEN FINAL');
        reporte.push('='.repeat(80));
        reporte.push(`Total procesados: ${documentosSeleccionados.length}`);
        reporte.push(`✓ Exitosos: ${paquetesExitosos.length}`);
        reporte.push(`⚠ Fallidos (RIPS): ${erroresRips.length}`);
        reporte.push(`⚠ Fallidos (Descarga): ${paquetesFallidos.length}`);
        reporte.push('='.repeat(80));

        // Guardar reporte como TXT
        await guardarReporte(directoryHandle, reporte);

        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);

        const totalFallidos = erroresRips.length + paquetesFallidos.length;
        
        if (paquetesExitosos.length > 0 && totalFallidos === 0) {
            showToast(
                'Proceso Completado', 
                `✓ ${paquetesExitosos.length} paquetes descargados correctamente. Ver reporte para detalles.`,
                'success',
                8000
            );
        } else if (paquetesExitosos.length > 0 && totalFallidos > 0) {
            showToast(
                'Proceso Completado con Errores', 
                `✓ ${paquetesExitosos.length} exitosos | ⚠ ${totalFallidos} fallidos. Ver reporte_descarga.txt para detalles.`,
                'warning',
                10000
            );
        } else {
            showToast(
                'Proceso Fallido', 
                `Todos los paquetes fallaron. Ver reporte_descarga.txt para detalles.`,
                'error',
                10000
            );
        }

    } finally {
        if (downloadButton) {
            downloadButton.disabled = false;
            downloadButton.innerHTML = originalHTML;
            downloadButton.style.opacity = '1';
            downloadButton.style.cursor = 'pointer';
        }

        if (botonBuscar) {
            botonBuscar.disabled = false;
            botonBuscar.style.opacity = '1';
            botonBuscar.style.cursor = 'pointer';
        }

        if (botonEnviar) {
            botonEnviar.disabled = false;
            botonEnviar.style.opacity = '1';
            botonEnviar.style.cursor = 'pointer';
        }
    }
}


/**
 * Descarga un paquete individual de factura.
 * Ejecuta RIPS, descarga el archivo ZIP y guarda en la carpeta seleccionada por el usuario.
 * 
 * @async
 * @function DescargarPaquete
 * @param {HTMLButtonElement} button - Elemento del botón que inició la descarga
 * @returns {Promise<void>}
 *  
 * @throws {Error} Si no se encuentra nFact o ID del documento
 * @throws {Error} Si el usuario no otorga permisos de escritura
 * @throws {Error} Si falla la ejecución de RIPS o la descarga
 * 
 */
async function DescargarPaquete(button) {
    const botonBuscar = document.getElementById("botonBuscar");
    const tipo = "json";
    const incluirXml = document.getElementById('incluirXML').checked;
    const row = button.closest('tr');
    const nfact = row.querySelector('td:nth-child(2)').textContent.trim();
    const id = button.getAttribute('data-id') || button.value;


    if (!nfact) {
        showToast('Error', `No se encontró Nfact para este documento`, 'error');
        return;
    }

    if (!id) {
        showToast('Error', `No se encontró ID para este documento`, 'error');
        return;
    }

    const originalHTML = button.innerHTML;
    button.disabled = true;
    button.innerHTML = 'Descargando...';
    button.style.opacity = '0.6';
    button.style.cursor = 'not-allowed';

    botonBuscar.disabled = true;
    botonBuscar.style.opacity = '0.6';
    botonBuscar.style.cursor = 'not-allowed';

    let directoryHandle;
    try {
        try {
            directoryHandle = await window.showDirectoryPicker();

            const permiso = await directoryHandle.requestPermission({ mode: 'readwrite' });
            if (permiso !== 'granted') {
                showToast('Error', 'Permiso denegado para escribir en la carpeta seleccionada.', 'error');
                return;
            }

        } catch (e) {
            showToast('Cancelado', 'Selección de carpeta cancelada.', 'error');
            return;
        }

        const toast = showToast('Procesando', `Descargando paquete...`, 'success', 30000, true);
        actualizarToastProgreso(toast, 0);

        const errorRips = await ejecutarRips(nfact);
        if (errorRips) {
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            showToast('Error', `Error al descargar ${nfact}, no hay JSON`, 'error');
            
            return;
        }

        actualizarToastProgreso(toast, 50);

        const downloadError = await descargarZip(id, tipo, incluirXml, directoryHandle);
        if (downloadError) {
            setTimeout(() => {
                toast.classList.add('fadeOut');
                setTimeout(() => toast.remove(), 300);
            }, 1000);
            showToast('Error', downloadError, 'error');

            return;
        }
        
        actualizarToastProgreso(toast, 100);
        setTimeout(() => {
            toast.classList.add('fadeOut');
            setTimeout(() => toast.remove(), 300);
        }, 1000);

        showToast('Éxito', `Factura ${nfact} descargada correctamente`, 'success');

    } finally {
        button.disabled = false;
        button.innerHTML = originalHTML;
        button.style.opacity = '1';
        button.style.cursor = 'pointer';

        botonBuscar.disabled = false;
        botonBuscar.style.opacity = '1';
        botonBuscar.style.cursor = 'pointer';
    }
}

/**
 * Guarda un reporte de descarga como archivo de texto (.txt) en la carpeta seleccionada.
 * El nombre del archivo incluye timestamp con fecha y hora de Bogotá.
 * 
 * @async
 * @function guardarReporte
 * @param {FileSystemDirectoryHandle} directoryHandle - Handle del directorio donde se guardará el reporte
 * @param {string[]} lineasReporte - Array de strings donde cada elemento es una línea del reporte
 * @returns {Promise<void>}
 * 
 * @throws {Error} Si falla la creación o escritura del archivo (capturado internamente)
 */
async function guardarReporte(directoryHandle, lineasReporte) {
    try {
        
        const ahora = new Date();
        const bogota = new Date(ahora.toLocaleString('en-US', { timeZone: 'America/Bogota' }));
        
        const dia = String(bogota.getDate()).padStart(2, '0');
        const mes = String(bogota.getMonth() + 1).padStart(2, '0');
        const año = bogota.getFullYear();
        const hora = String(bogota.getHours()).padStart(2, '0');
        const minuto = String(bogota.getMinutes()).padStart(2, '0');
        const segundo = String(bogota.getSeconds()).padStart(2, '0');
        
        const timestamp = `${dia}-${mes}-${año}_${hora}-${minuto}-${segundo}`;
        const nombreArchivo = `reporte_descarga_${timestamp}.txt`;
        
        const fileHandle = await directoryHandle.getFileHandle(nombreArchivo, { create: true });
        const writable = await fileHandle.createWritable();
        
        const contenido = lineasReporte.join('\n');
        await writable.write(contenido);
        await writable.close();
        
        console.log(`✓ Reporte guardado: ${nombreArchivo}`);
    } catch (error) {
        console.error('Error al guardar reporte:', error);
        showToast('Advertencia', 'No se pudo guardar el archivo de reporte', 'warning', 5000);
    }
}

/**
 * Descarga el log de errores de una factura desde una URL almacenada en el botón.
 * Después de descargar, limpia la URL temporal del botón.
 * 
 * @param {HTMLButtonElement} btn - Botón que contiene la URL y nombre del archivo en sus data attributes
 * 
 * @description
 * Requiere que el botón tenga:
 * - data-download-url: URL blob temporal del archivo
 * - data-filename: Nombre sugerido para el archivo (opcional, por defecto 'logs.txt')
 * 
 */
function descargarLogsGenerales(btn) {
    const url = btn.dataset.downloadUrl;
    const filename = btn.dataset.filename || 'logs.txt';

    if (!url) {
        alert('No hay logs disponibles.');
        return;
    }

    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();

    URL.revokeObjectURL(url);
    delete btn.dataset.downloadUrl;
}




// ==========================================================
// FUNCIONES PARA MANEJO DE NOTIFICACIONES
// ==========================================================

/**
 * Muestra una notificación toast en la interfaz de usuario.
 * Soporta diferentes tipos visuales (éxito, error, advertencia) y barra de progreso opcional.
 * 
 * @function showToast
 * @param {string} title - Título del toast mostrado en negrita
 * @param {string} message - Mensaje principal del toast
 * @param {('success'|'error'|'warning'|'info')} [type='success'] - Tipo de toast que determina color y estilo
 * @param {number} [duration=6000] - Duración en milisegundos antes de ocultarse automáticamente
 * @param {boolean} [showProgress=false] - Si true, muestra barra de progreso controlable manualmente
 * @returns {HTMLElement} Elemento DOM del toast creado (útil para actualizar progreso)
 * 
 * 
 */

function showToast(title, message, type = 'success', duration = 6000, showProgress = false) {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    toast.innerHTML = `
        <div class="toast-content">
            <strong>${title}</strong>
            <button class="close-toast" onclick="this.parentElement.parentElement.remove()">✖</button>
            <p>${message}</p>
            ${showProgress ? `
            <div class="toast-progress-container">
                <div class="toast-progress-bar" style="width: 0%;"></div>
            </div>` : ''}
        </div>
    `;

    container.appendChild(toast);

    if (!showProgress) {
        setTimeout(() => {
            if (toast.parentElement) {
                toast.classList.add('fadeOut');
                setTimeout(() => {
                    container.removeChild(toast);
                }, 300);
            }
        }, duration);
    }


    return toast;
}

/**
 * Actualiza el porcentaje de la barra de progreso en un toast existente.
 * Solo funciona en toasts creados con showProgress=true.
 * 
 * @function actualizarToastProgreso
 * @param {HTMLElement} toast - Elemento DOM del toast que contiene la barra de progreso
 * @param {number} porcentaje - Porcentaje de progreso entre 0 y 100
 * @returns {void}
 */

function actualizarToastProgreso(toast, porcentaje) {
    const progressBar = toast.querySelector('.toast-progress-bar');
    if (progressBar) {
        progressBar.style.width = `${porcentaje}%`;
    }
}






// ==========================================================
// FUNCIONES UTILES
// ==========================================================

/**
 * Alterna la expansión y contracción de contenido colapsable con animación suave.
 * Utilizado para mostrar/ocultar listas largas de errores, detalles o información extensa.
 * 
 * @function toggleExpand
 * @param {HTMLButtonElement} button - Botón que activa la expansión (debe tener hermano .expand-content)
 * @returns {void}
 * 
 */
function toggleExpand(button) {
    const content = button.nextElementSibling;
    const isExpanded = content.classList.contains('expanded');

    if (isExpanded) {
        content.style.maxHeight = content.scrollHeight + 'px';
        requestAnimationFrame(() => {
            content.style.maxHeight = '7em';
        });
        content.classList.remove('expanded');
        button.classList.remove('expanded');
    } else {
        content.style.maxHeight = content.scrollHeight + 'px';
        content.classList.add('expanded');
        button.classList.add('expanded');

        content.addEventListener('transitionend', function clearMax() {
            if (content.classList.contains('expanded')) {
                content.style.maxHeight = 'none';
            }
            content.removeEventListener('transitionend', clearMax);
        });
    }
}


/**
 * Ejecuta el procedimiento almacenado RIPS para una factura específica.
 * Este procedimiento valida y genera los archivos RIPS necesarios.
 * 
 * @async
 * @function ejecutarRips
 * @param {string} nfact - Número de factura a procesar
 * @returns {Promise<string|null>} Mensaje de error si falla, null si es exitoso
 * 
 * 
 */
async function ejecutarRips(nfact) {
    try {
        const response = await fetch(`/api/sql/ejecutarRips?Nfact=${encodeURIComponent(nfact)}`);
        const result = await response.text();

        if (!response.ok) {
            return `Error al ejecutar RIPS para ${nfact}: ${result}`;
        }

        return null;
    } catch (error) {
        return `Error de red al ejecutar RIPS para ${nfact}: ${error.message}`;
    }
}

/**
 * Descarga un archivo ZIP que contiene los archivos de una factura (JSON y opcionalmente XML).
 * Guarda el archivo directamente en la carpeta seleccionada por el usuario usando File System Access API.
 * 
 * @async
 * @function descargarZip
 * @param {string} id - ID del documento (idMovDoc) a descargar
 * @param {string} tipo - Tipo de archivo a incluir en el ZIP (generalmente "json")
 * @param {boolean} xml - Si true, incluye el archivo XML en el ZIP; si false, solo JSON
 * @param {FileSystemDirectoryHandle} directoryHandle - Handle del directorio donde se guardará el archivo
 * @returns {Promise<boolean|string>} false si es exitoso, mensaje de error (string) si falla
 * 
 * @throws {Error} Captura errores de red y retorna mensaje descriptivo como string
 */
async function descargarZip(id, tipo, xml, directoryHandle) {
    const host = window.location.hostname;
    const url = `https://${host}:9876/facturas/generarzip/${id}/${tipo}/${xml}`; 

    try {
        const response = await fetch(url);

        if (!response.ok) {
            const errorText = await response.text();
            return errorText;
        }

        const blob = await response.blob();
        let fileName = `certificado_${id}.zip`;

        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition && contentDisposition.includes('filename=')) {
            fileName = contentDisposition.split('filename=')[1].replace(/['"]/g, '').trim();
        }

        const fileHandle = await directoryHandle.getFileHandle(fileName, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();

        return false;
    } catch (error) {
        return `Error de red para ID ${id}: ${error.message}`;
    }
}

/**
 * Alterna el estado de selección de todos los checkboxes de las filas.
 * 
 * @param {HTMLInputElement} checkbox - Checkbox principal "Seleccionar todo"
 */
function toggleSelectAll(checkbox) {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    checkboxes.forEach(cb => cb.checked = checkbox.checked);
}

/**
 * Selecciona filas según su estado de validación y opcionalmente las filtra visualmente.
 * 
 * @param {string} tipo - Tipo de selección: 'todos', 'pendientes', 'enviados' o 'ninguno'
 * 
 */
function seleccionarPorEstado(tipo) {
    const filas = document.querySelectorAll('#tablaBody tr');
    let totalSeleccionadas = 0;

    filas.forEach(fila => {
        const estado = fila.querySelector('.estado-cell')?.textContent?.toLowerCase().trim();
        const checkbox = fila.querySelector('.filaCheckbox');

        if (!checkbox) return;

        switch (tipo) {
            case 'todos':
                checkbox.checked = true;
                totalSeleccionadas++;
                fila.style.display = '';
                break;
            case 'pendientes':
                const esPendiente = estado === 'pendiente';
                checkbox.checked = esPendiente;
                fila.style.display = esPendiente ? '' : 'none';
                if (esPendiente) totalSeleccionadas++;
                break;

            case 'enviados':
                const esEnviado = estado === 'enviado';
                checkbox.checked = esEnviado;
                fila.style.display = esEnviado ? '' : 'none';
                if (esEnviado) totalSeleccionadas++;
                break;
            case 'ninguno':
                checkbox.checked = false;
                fila.style.display = '';
                break;
        }
    });
    
    const selectAllCheckbox = document.getElementById('selectAll');
    if (selectAllCheckbox) {
        selectAllCheckbox.checked = tipo === 'todos';
    }

    verificarCheckboxes();
}

/**
 * Copia texto al portapapeles del usuario.
 * Utiliza Clipboard API si está disponible, de lo contrario usa execCommand (fallback).
 * 
 * @param {string} texto - Texto a copiar al portapapeles
 * @returns {Promise<void>} Promesa que se resuelve cuando el texto se copia exitosamente
 * @throws {Error} Si no se puede copiar el texto
 */
function copiarAlPortapapeles(texto) {
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(texto);
    } 

    else {
        return new Promise((resolve, reject) => {
            const textarea = document.createElement('textarea');
            textarea.value = texto;
            textarea.style.position = 'fixed';
            textarea.style.left = '-9999px';
            textarea.style.top = '-9999px';
            
            document.body.appendChild(textarea);
            textarea.focus();
            textarea.select();
            
            try {
                const exitoso = document.execCommand('copy');
                document.body.removeChild(textarea);
                
                if (exitoso) {
                    resolve();
                } else {
                    reject(new Error('No se pudo copiar'));
                }
            } catch (err) {
                document.body.removeChild(textarea);
                reject(err);
            }
        });
    }
}

/**
 * Carga la lista de terceros (entidades/clientes) desde el servidor
 * y puebla un elemento select con las opciones.
 * 
 * @returns {Promise<void>}
 * @throws {Error} Si falla la petición al servidor
 */
async function cargarTerceros() {
    try {
        const response = await fetch('/filtros/terceros');
        const data = await response.json();
        const select = document.getElementById('idTercero');

        data.forEach(tercero => {
            const option = document.createElement('option');
            option.value = tercero.idTerceroKey;
            option.textContent = tercero.nomTercero;
            select.appendChild(option);
        });
    } catch (error) {
        console.error("Error al cargar terceros:", error);
    }
}

/**
 * Verifica cuántos checkboxes están seleccionados y habilita/deshabilita
 * los botones de descarga y envío masivo según la cantidad.
 *
 */
function verificarCheckboxes() {
    const checkboxes = document.querySelectorAll('.filaCheckbox');
    const descargarPaquetesBtn = document.getElementById('DescargarPaquetesBtn');
    const enviarPaquetesBtn = document.getElementById('enviarPaquetesBtn');
    const ValidarCuvBtn = document.getElementById('ValidarCuvBtn')
    
    const cantidadSeleccionados = Array.from(checkboxes).filter(checkbox => checkbox.checked).length;

    document.getElementById("contadorSeleccionados").textContent = `SELECCIONADO: ${cantidadSeleccionados}`;

    descargarPaquetesBtn.disabled = cantidadSeleccionados < 2; 

    enviarPaquetesBtn.disabled = cantidadSeleccionados < 2;

    ValidarCuvBtn.disabled = cantidadSeleccionados < 1;
}

/**
 * Valida el Código Único de Validación (CUV) de las facturas seleccionadas
 * contra la API del validador y guarda la respuesta en base de datos.
 *
 * Itera sobre cada fila seleccionada, envía el CUV a /api/validador/consultar-cuv
 * y si la respuesta es exitosa, persiste el resultado en /api/validador/actualizar-respuesta.
 *
 * @throws Muestra toast de error por cada CUV que falle, sin interrumpir los demás.
 */
async function ValidarCuv() {
    console.log("=== ValidarCuv ejecutada ===");
    const checkboxes = document.querySelectorAll(".filaCheckbox:checked");

    if (checkboxes.length === 0) {
    showToast(
        "Error",
        "No hay filas seleccionadas para validar CUV",
        "error",
    );
    return;
    }

    const botonValidar = document.getElementById("ValidarCuvBtn");
    let originalHTML = botonValidar.innerHTML;
    botonValidar.disabled = true;
    botonValidar.innerHTML = "Validando...";
    botonValidar.style.opacity = "0.6";
    botonValidar.style.cursor = "not-allowed";

    try {
    const documentos = Array.from(checkboxes).map((cb) => {
        const fila = cb.closest("tr");
        const cuv = fila.querySelector(".valor-cuv")?.textContent.trim();
        const nFact = fila
        .querySelector("td:nth-child(2)")
        .textContent.trim();
        return { cuv, nFact };
    });

    let errores = [];

    for (const doc of documentos) {
        try {
        const response = await fetch(
          `https://${host}:9876/api/validador/consultar-cuv`,
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${sessionStorage.getItem("token")}`,
            },
            body: JSON.stringify({ codigoUnicoValidacion: doc.cuv }),
          },
        );

        const text = await response.text();

        if (!response.ok) {
            errores.push({ cuv: doc.cuv, error: text });
            console.error(`Error validando CUV ${doc.cuv}:`, text)
            showToast(
                "Error",
                `Factura ${doc.nFact}: ${text}`, 
                "error",
            );
        } else {
            showToast(
            "Éxito",
            `CUV de Factura ${doc.nFact} validado correctamente`,
            "success",
            );
            console.log(`Respuesta para ${doc.cuv}:`, text);

            const guardarResp = await fetch(
              `https://${host}:9876/api/validador/actualizar-respuesta`,
              {
                method: "POST",
                headers: {
                  "Content-Type": "application/json",
                  Authorization: `Bearer ${sessionStorage.getItem("token")}`,
                },
                body: JSON.stringify({
                  nFact: doc.nFact,
                  mensajeRespuesta: JSON.stringify(JSON.parse(text), null, 2),
                }),
              },
            );

            const guardarRespText = await guardarResp.text();

            if (!guardarResp.ok) {
            console.warn(`No se guardó respuesta para ${doc.nFact}: ${guardarRespText}`);
            } else {
            if (guardarRespText.includes("No se encontró registro")) {
                showToast(
                "Error",
                `Factura ${doc.nFact}: ${guardarRespText}`,
                "error",
                );
            } else {
                console.log(`Guardado en BD para ${doc.nFact}`);
            }
            }
        }
        } catch (err) {
        errores.push({ cuv: doc.cuv, error: err.message });
        showToast(
            "Error",
            `Fallo en la solicitud para Factura ${doc.nFact}`,
            "error",
        );
        }
    }

    if (errores.length === 0) {
        showToast(
        "Éxito",
        "Todos los CUVs se procesaron correctamente",
        "success",
        );
    }
    } finally {
    botonValidar.disabled = false;
    botonValidar.innerHTML = originalHTML;
    botonValidar.style.opacity = "1";
    botonValidar.style.cursor = "pointer";
    }
}



