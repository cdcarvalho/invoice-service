#!/bin/bash
echo "Gerando dados em lote no PostgreSQL (isso pode levar alguns segundos)..."
docker exec -i pg_invoice psql -U myuser -d invoice_db < generate_high_volume_data.sql
echo "Geração concluída com sucesso!"
