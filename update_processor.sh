#!/bin/bash
FILE="src/main/java/com/backing/invoice/batch/BatchConfig.java"

# Add import if missing
grep -q "com.backing.invoice.repository.LancamentoRepository" $FILE || sed -i '/import com.backing.invoice.repository.FaturaRepository;/a import com.backing.invoice.repository.LancamentoRepository;' $FILE

# Update processor
sed -i 's/public ItemProcessor<Conta, Fatura> processor()/public ItemProcessor<Conta, Fatura> processor(LancamentoRepository lancamentoRepository)/g' $FILE

sed -i 's/return new Fatura(conta.getId(), mesRef, new BigDecimal("150.00"), "CALCULADA");/BigDecimal valorTotal = lancamentoRepository.sumValorByContaId(conta.getId());\n            return new Fatura(conta.getId(), mesRef, valorTotal, "CALCULADA");/g' $FILE
