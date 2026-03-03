#!/bin/bash
FILE="src/main/java/com/backing/invoice/batch/BatchConfig.java"
sed -i '/return conta -> {/a \
            log.info("Processando conta_id: {} para calculo de fatura", conta.getId());' $FILE

sed -i '/int updated = contaRepository.updateStatusToProcessada/i \
                    log.info("Salvando fatura e atualizando status para conta_id: {}", fatura.getContaId());' $FILE
