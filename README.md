# Projeto de Fechamento de Faturas Batch

## Arquitetura e Escalonamento
O sistema utiliza Spring Batch com particionamento baseado em hash do ID da conta para escalar e dividir os dados de processamento.
Cada particão trata de ler suas contas e consolidar as faturas.

### Restart Seguro
O Job é tolerante a falhas porque a atualização do status da conta para 'PROCESSADA' ocorre na mesma transação que a gravação da fatura.
Além disso, há uma Unique Constraint na fatura por conta e mês. Se o processo cair no meio do chunk, ele será refeito. O writer trata exceções para garantir que registros já persistidos não quebrem o processamento (neste exemplo, usamos a idempotência via `ContaRepository.updateStatusToProcessada`).

### Como escalar para 5 milhões
- Ajustar `chunkSize` para valores maiores (ex: 500 ou 1000).
- Utilizar `Remote Partitioning` ou `Remote Chunking` no Spring Batch usando brokers (ex: Kafka, RabbitMQ) para distribuir chunks para vários Pods em um cluster Kubernetes.
- Aumentar o número de Threads do Pool se for em único pod robusto.

## Rodando Localmente
1. Suba um banco PostgreSQL ou use docker: `docker run --name pg_invoice -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=mypassword -e POSTGRES_DB=invoice_db -p 5432:5432 -d postgres`
2. Execute o projeto usando maven: `./mvnw spring-boot:run`
