-- Создание ключевого пространства
CREATE KEYSPACE IF NOT EXISTS dissertation
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};

-- Использовать созданное ключевое пространство
USE dissertation;

-- Создание таблицы для хранения информации о заказах
CREATE TABLE IF NOT EXISTS simple_order_info (
    id UUID PRIMARY KEY,
    user_profile TEXT,
    payment TEXT,
    product_list TEXT,
    quantity INT,
    total_price TEXT,
    order_date TIMESTAMP,
    status TEXT,
    delivery_method TEXT,
    delivery_address TEXT,
    estimated_delivery_date TIMESTAMP,
    tracking_number TEXT,
    payment_id TEXT,
    discount_amount TEXT,
    tax_amount TEXT,
    metadata MAP<TEXT, TEXT>
);

-- Создание таблицы для хранения сжатой информации о заказах
CREATE TABLE IF NOT EXISTS compressed_order_info (
    id UUID PRIMARY KEY,
    protocol TEXT,
    compressed_payload BLOB
);