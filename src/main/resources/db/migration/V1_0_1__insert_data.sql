/*******************************************CURRENCIES*******************************************/

insert into currencies (id, created_at, updated_at, version, name, symbol)
values (nextval('currencies_id_seq'), now(), now(), 0, 'BITCOIN', 'BTC'),
       (nextval('currencies_id_seq'), now(), now(), 0, 'TETHER', 'USDT'),
       (nextval('currencies_id_seq'), now(), now(), 0, 'ETHEREUM', 'ETH'),
       (nextval('currencies_id_seq'), now(), now(), 0, 'BINANCE', 'BNB'),
       (nextval('currencies_id_seq'), now(), now(), 0, 'SOLANA', 'SOL'),
       (nextval('currencies_id_seq'), now(), now(), 0, 'TRON', 'TRX');


/*******************************************PAIRS*******************************************/


INSERT INTO pairs (
    id, created_at, updated_at, version, symbol, base_currency_id, quote_currency_id
)
SELECT
    nextval('pairs_id_seq')                    AS id,
    now()                                      AS created_at,
    now()                                      AS updated_at,
    0                                          AS version,
    c.symbol || '-USDT'                        AS symbol,
    c.id                                       AS base_currency_id,
    u.id                                       AS quote_currency_id
FROM currencies c
         JOIN currencies u
              ON u.symbol = 'USDT'
         LEFT JOIN pairs p
                   ON p.symbol = c.symbol || '-USDT'
WHERE c.symbol <> 'USDT'        -- avoid USDT-USDT
  AND p.id IS NULL;             -- only create if pair doesn't already exist

/*******************************************EXCHANGES*******************************************/

insert into exchanges (id, created_at, updated_at, version, name, private_api_url, private_ws_url, public_api_url,
                       public_ws_url, status)
values (nextval('exchanges_id_seq'), now(), now(), 0, 'RAMZINEX', '', '', 'https://publicapi.ramzinex.com','wss://websocket.ramzinex.com/websocket','ACTIVE'),
       (nextval('exchanges_id_seq'), now(), now(), 0, 'NOBITEX', '', '','https://apiv2.nobitex.ir','wss://ws.nobitex.ir/connection/websocket','ACTIVE');


/*******************************************CURRENCY_EXCHANGES*******************************************/

WITH chosen_exchange AS (
    -- pick the exchange you want
    SELECT id FROM exchanges WHERE name = 'NOBITEX'  -- or 'NOBITEX'
),
     items(symbol, scale_override) AS (
         -- manually list what you want to write
         VALUES ('BTC', 6),
                ('USDT', 2),
                ('ETH', 5)
     ),
     joined AS (
         SELECT i.symbol,
                i.scale_override,
                c.id  AS currency_id,
                ce.id AS exchange_id
         FROM items i
                  JOIN currencies c     ON c.symbol = i.symbol
                  CROSS JOIN chosen_exchange ce
     ),
     updated AS (
UPDATE currency_exchanges x
SET    scale_override = j.scale_override,
       updated_at     = now()
    FROM   joined j
WHERE  x.exchange_id = j.exchange_id
  AND  x.currency_id = j.currency_id
    RETURNING x.exchange_id, x.currency_id
    )
INSERT INTO currency_exchanges (
    id, created_at, updated_at, version,
    exchange_symbol, scale_override, currency_id, exchange_id
)
SELECT
    nextval('currency_exchanges_id_seq'),
    now(), now(), 0,
    j.symbol, j.scale_override, j.currency_id, j.exchange_id
FROM joined j
         LEFT JOIN updated u
                   ON u.exchange_id = j.exchange_id
                       AND u.currency_id = j.currency_id
WHERE u.exchange_id IS NULL;   -- only insert rows that weren't updated



/*******************************************PAIR_EXCHANGES*******************************************/


insert into pair_exchanges (id, created_at, updated_at, version, exchange_symbol, maker_fee_bps, max_order_size,
                            min_notional, pack_size, status, step_size, taker_fee_bps, tick_size, exchange_id, pair_id)
values (nextval('pair_exchanges_id_seq'), now(), now(), 0, '12', 0.3, 10000, 2, 1, 'ACTIVE', 0.000001, 0.01, 1, 1, 1),
       (nextval('pair_exchanges_id_seq'), now(), now(), 0, 'BTCUSDT', 0.1, 10000, 2, 1, 'ACTIVE', 0.000001, 0.13, 1, 2, 1);

