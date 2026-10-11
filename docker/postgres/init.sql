-- Una base de datos por microservicio (database-per-service): ningun servicio lee las tablas de otro,
-- solo se hablan por API REST o eventos Kafka. Se ejecuta UNA vez, al crear el volumen vacio.
CREATE DATABASE clientes_db;
CREATE DATABASE cuentas_db;
CREATE DATABASE pagos_db;
CREATE DATABASE batch_db;
