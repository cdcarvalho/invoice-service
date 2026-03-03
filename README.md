# Projeto de Fechamento de Faturas Batch (Arquitetura Distribuída)

## Arquitetura e Escalonamento

Esta versão do projeto foi re-arquitetada para um modelo de processamento verdadeiramente distribuído, ideal para ambientes de nuvem como Kubernetes. A solução utiliza o padrão **Database as a Queue** para coordenar múltiplos workers (pods).

O sistema é dividido em dois Jobs:
1.  **`masterJob`**: Atua como o "Mestre" ou "Produtor". Sua única responsabilidade é criar as definições de trabalho (partições) e inseri-las como tarefas em uma tabela de fila (`BATCH_PARTITION_QUEUE`) no banco de dados. Ele é projetado para rodar apenas uma vez por ciclo (ex: uma vez por dia).
2.  **`workerJob`**: Atua como o "Operário" ou "Consumidor". Este job roda continuamente em todas as instâncias da aplicação. Cada worker compete para "capturar" tarefas da fila de forma atômica e segura, executa o processamento da fatura para aquela partição, e atualiza o status da tarefa.

Este modelo permite escalabilidade horizontal real: para aumentar a capacidade de processamento, basta adicionar mais pods/instâncias da aplicação.

### Restart Seguro
A tolerância a falhas é garantida em múltiplos níveis:
- **Nível de Chunk**: O processamento de cada partição ainda é feito em chunks transacionais.
- **Nível de Partição**: Se um pod falhar no meio do processamento de uma partição, a tarefa na fila permanecerá com o status `CLAIMED`. Será necessário um mecanismo de "lease" ou timeout para que outra instância possa re-processar tarefas presas (fora do escopo desta implementação simples).
- **Nível de Job**: O `masterJob` utiliza os parâmetros do Spring Batch para garantir que ele só crie as partições uma única vez para um determinado dia.

## Rodando e Testando o Ambiente Distribuído Localmente

### 1. Pré-requisitos
- O Docker deve estar instalado e rodando.
- Suba uma instância do PostgreSQL:
  ```bash
  docker run --name pg_invoice -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=mypassword -e POSTGRES_DB=invoice_db -p 5432:5432 -d postgres
  ```
- Compile o projeto para garantir que todas as dependências estão baixadas:
  ```bash
  ./mvnw clean install -DskipTests
  ```

### 2. Simulação dos Múltiplos Pods
Abra 3 terminais diferentes e inicie uma instância da aplicação em cada um, em portas diferentes, para simular 3 pods rodando em paralelo.

**Terminal 1 (Pod Worker 1):**
```bash
./mvnw spring-boot:run -Dserver.port=8081
```

**Terminal 2 (Pod Worker 2):**
```bash
./mvnw spring-boot:run -Dserver.port=8082
```

**Terminal 3 (Pod Worker 3):**
```bash
./mvnw spring-boot:run -Dserver.port=8083
```
*Observação: Opcionalmente, um dos pods pode atuar como master, mas na prática todos rodam o mesmo código e poderiam executar qualquer um dos jobs.*

### 3. Execução e Observação do Comportamento
O projeto agora é controlado por agendamento (`@Scheduled`):

1.  **Master Job (Criação das Tarefas)**:
    - O `masterJob` está agendado para rodar **à 1h da manhã (`cron = "0 0 1 * * ?")`**. Apenas uma das 3 instâncias conseguirá executá-lo com sucesso (as outras falharão ao tentar rodar com os mesmos parâmetros, o que é o comportamento esperado).
    - **Para testar manualmente**, você pode alterar temporariamente a anotação de schedule no `JobScheduler.java` para algo que rode a cada minuto, ou criar um endpoint de teste.
    - Ao rodar, o log de um dos pods mostrará: `MASTER: Criando partições...` e `MASTER: 10 partições criadas com sucesso.`
    - Você pode verificar no banco que a tabela `batch_partition_queue` foi populada com 10 registros com status `PENDING`.

2.  **Worker Jobs (Processamento das Tarefas)**:
    - O `workerJob` está agendado para rodar **a cada 5 minutos** em **TODOS** os pods.
    - Assim que as partições forem criadas pelo master, no próximo ciclo de 5 minutos, você verá nos logs dos 3 terminais os workers competindo e processando as tarefas.
    - **Logs para observar**:
      - `SCHEDULER: Iniciando workerJob para processar partições.` (em todos os pods)
      - `WORKER: Partição X capturada para Job Execution ID: Y` (distribuído entre os pods)
      - `Iniciando leitura para particao X`
      - `Processando conta_id: Z ...`
    - Você pode consultar a tabela `batch_partition_queue` durante o processamento para ver os status mudando de `PENDING` para `CLAIMED` e depois para `COMPLETED`. Cada linha terá um `worker_pod_id` diferente, mostrando a distribuição do trabalho.
