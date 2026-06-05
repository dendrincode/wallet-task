package com.wallet.config;

import com.wallet.entity.CurrencyType;
import com.wallet.entity.Transaction;
import com.wallet.entity.WalletStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import io.r2dbc.spi.ConnectionFactory;

import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        var dialect = DialectResolver.getDialect(connectionFactory);
        List<Converter<?, ?>> converters = List.of(
            new WalletStatusWritingConverter(),
            new WalletStatusReadingConverter(),
            new CurrencyTypeWritingConverter(),
            new CurrencyTypeReadingConverter(),
            new TransactionTypeWritingConverter(),
            new TransactionTypeReadingConverter()
        );
        return R2dbcCustomConversions.of(dialect, converters);
    }

    @WritingConverter
    static class WalletStatusWritingConverter implements Converter<WalletStatus, String> {
        @Override
        public String convert(WalletStatus source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class WalletStatusReadingConverter implements Converter<String, WalletStatus> {
        @Override
        public WalletStatus convert(String source) {
            return WalletStatus.valueOf(source);
        }
    }

    @WritingConverter
    static class CurrencyTypeWritingConverter implements Converter<CurrencyType, String> {
        @Override
        public String convert(CurrencyType source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class CurrencyTypeReadingConverter implements Converter<String, CurrencyType> {
        @Override
        public CurrencyType convert(String source) {
            return CurrencyType.valueOf(source);
        }
    }

    @WritingConverter
    static class TransactionTypeWritingConverter implements Converter<Transaction.TransactionType, String> {
        @Override
        public String convert(Transaction.TransactionType source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class TransactionTypeReadingConverter implements Converter<String, Transaction.TransactionType> {
        @Override
        public Transaction.TransactionType convert(String source) {
            return Transaction.TransactionType.valueOf(source);
        }
    }
}
