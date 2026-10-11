-- =====================================================================
-- Tablas de negocio del batch-service (los metadatos de Spring Batch los crea el propio framework)
-- Todas son idempotentes (CREATE IF NOT EXISTS + UPSERT en los writers) => un job se puede reejecutar sin duplicar datos
-- =====================================================================

-- Job 1: Reporte de Transacciones Diarias
CREATE TABLE IF NOT EXISTS transaccion_procesada (
    id              BIGINT PRIMARY KEY,
    fecha           DATE,
    monto           NUMERIC(16,2),
    tipo            VARCHAR(30),
    estado          VARCHAR(10) NOT NULL,          -- VALIDA | ANOMALA
    motivo_anomalia VARCHAR(250),
    procesado_en    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    fecha              DATE PRIMARY KEY,
    total_creditos     NUMERIC(18,2) NOT NULL,
    total_debitos      NUMERIC(18,2) NOT NULL,
    cant_transacciones INTEGER NOT NULL,
    cant_anomalias     INTEGER NOT NULL
);

-- Job 2: Calculo de Intereses Mensuales
CREATE TABLE IF NOT EXISTS cuenta_staging (
    cuenta_id BIGINT PRIMARY KEY,
    nombre    VARCHAR(100) NOT NULL,
    saldo     NUMERIC(16,2) NOT NULL,
    edad      INTEGER NOT NULL,
    tipo      VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS interes_mensual (
    cuenta_id     BIGINT NOT NULL,
    periodo       VARCHAR(7) NOT NULL,             -- AAAA-MM
    nombre        VARCHAR(100) NOT NULL,
    tipo          VARCHAR(20) NOT NULL,
    saldo         NUMERIC(16,2) NOT NULL,
    tasa_mensual  NUMERIC(8,5) NOT NULL,
    interes       NUMERIC(16,2) NOT NULL,
    nuevo_saldo   NUMERIC(16,2) NOT NULL,
    calculado_en  TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (cuenta_id, periodo)
);

-- Job 3: Estados de Cuenta Anuales
CREATE TABLE IF NOT EXISTS movimiento_anual (
    id          BIGSERIAL PRIMARY KEY,
    cuenta_id   BIGINT NOT NULL,
    fecha       DATE NOT NULL,
    transaccion VARCHAR(20) NOT NULL,
    monto       NUMERIC(16,2) NOT NULL,            -- siempre positivo; el signo lo da el tipo de transaccion
    descripcion VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS estado_cuenta_anual (
    cuenta_id        BIGINT NOT NULL,
    anio             INTEGER NOT NULL,
    cant_movimientos INTEGER NOT NULL,
    total_depositos  NUMERIC(18,2) NOT NULL,
    total_egresos    NUMERIC(18,2) NOT NULL,
    saldo_neto       NUMERIC(18,2) NOT NULL,
    estado           VARCHAR(10) NOT NULL,          -- OK | REVISAR
    generado_en      TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (cuenta_id, anio)
);

-- Transversales: trazabilidad de rechazos y conciliacion de integridad
CREATE TABLE IF NOT EXISTS registro_rechazado (
    id            BIGSERIAL PRIMARY KEY,
    job_name      VARCHAR(80) NOT NULL,
    step_name     VARCHAR(80) NOT NULL,
    detalle       VARCHAR(400),
    motivo        VARCHAR(300),
    registrado_en TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conciliacion_batch (
    id                  BIGSERIAL PRIMARY KEY,
    job_name            VARCHAR(80) NOT NULL,
    job_execution_id    BIGINT NOT NULL,
    leidos              BIGINT NOT NULL,
    escritos            BIGINT NOT NULL,
    filtrados           BIGINT NOT NULL,
    omitidos_proceso    BIGINT NOT NULL,
    omitidos_escritura  BIGINT NOT NULL,
    omitidos_lectura    BIGINT NOT NULL,
    consistente         BOOLEAN NOT NULL,
    registrado_en       TIMESTAMP NOT NULL DEFAULT now()
);
