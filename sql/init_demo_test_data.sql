SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `demo`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `demo`;

DROP VIEW IF EXISTS `tb_seckill_voucher`;
DROP VIEW IF EXISTS `tb_voucher`;

DROP TABLE IF EXISTS `voucher_order`;
DROP TABLE IF EXISTS `seckill_voucher`;
DROP TABLE IF EXISTS `voucher`;
DROP TABLE IF EXISTS `home_recommendation`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `user_account`;
DROP TABLE IF EXISTS `shop`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) NOT NULL,
  `password` varchar(128) DEFAULT NULL,
  `nick_name` varchar(64) NOT NULL,
  `icon` varchar(255) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `shop` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `type_id` bigint NOT NULL,
  `images` varchar(1024) DEFAULT NULL,
  `area` varchar(128) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `x` double DEFAULT NULL,
  `y` double DEFAULT NULL,
  `avg_price` bigint DEFAULT 0,
  `sold` int DEFAULT 0,
  `comments` int DEFAULT 0,
  `score` int DEFAULT 0,
  `open_hours` varchar(128) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_shop_type_id` (`type_id`),
  KEY `idx_shop_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `level` int NOT NULL DEFAULT 1,
  `status` int NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `sub_title` varchar(255) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `images` varchar(1024) DEFAULT NULL,
  `price` bigint NOT NULL DEFAULT 0,
  `stock` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_shop_id` (`shop_id`),
  KEY `idx_product_category_id` (`category_id`),
  KEY `idx_product_name` (`name`),
  CONSTRAINT `fk_product_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`),
  CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `home_recommendation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `sub_title` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `rank` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 1,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_home_recommend_biz_type` (`biz_type`),
  KEY `idx_home_recommend_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_account` (
  `user_id` bigint NOT NULL,
  `balance` bigint NOT NULL DEFAULT 0,
  `frozen_balance` bigint NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_account_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `voucher` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `shop_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `sub_title` varchar(255) DEFAULT NULL,
  `rules` varchar(1024) DEFAULT NULL,
  `pay_value` bigint NOT NULL,
  `actual_value` bigint NOT NULL,
  `type` int NOT NULL DEFAULT 0,
  `status` int NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_voucher_shop_id` (`shop_id`),
  KEY `idx_voucher_status` (`status`),
  CONSTRAINT `fk_voucher_shop` FOREIGN KEY (`shop_id`) REFERENCES `shop` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `seckill_voucher` (
  `voucher_id` bigint NOT NULL,
  `stock` int NOT NULL DEFAULT 0,
  `begin_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  PRIMARY KEY (`voucher_id`),
  CONSTRAINT `fk_seckill_voucher_voucher` FOREIGN KEY (`voucher_id`) REFERENCES `voucher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `voucher_order` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `voucher_id` bigint NOT NULL,
  `status` int NOT NULL DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已取消',
  `order_no` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `pay_time` datetime DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`),
  KEY `idx_order_voucher_id` (`voucher_id`),
  KEY `idx_order_status` (`status`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_order_voucher` FOREIGN KEY (`voucher_id`) REFERENCES `voucher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE VIEW `tb_voucher` AS
SELECT
  `id`,
  `shop_id`,
  `title`,
  `sub_title`,
  `rules`,
  `pay_value`,
  `actual_value`,
  `type`,
  `status`,
  `create_time`,
  `update_time`
FROM `voucher`;

CREATE VIEW `tb_seckill_voucher` AS
SELECT
  `voucher_id`,
  `stock`,
  `begin_time`,
  `end_time`
FROM `seckill_voucher`;

INSERT INTO `user` (`id`, `phone`, `password`, `nick_name`, `icon`, `create_time`, `update_time`) VALUES
  (1, '13800138000', NULL, 'test_user_01', '', '2026-03-20 09:00:00', '2026-03-20 09:00:00'),
  (2, '13800138001', NULL, 'test_user_02', '', '2026-03-20 09:05:00', '2026-03-20 09:05:00'),
  (3, '13900139000', NULL, 'buyer_demo', '', '2026-03-20 09:10:00', '2026-03-20 09:10:00');

INSERT INTO `shop` (`id`, `name`, `type_id`, `images`, `area`, `address`, `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`, `create_time`, `update_time`) VALUES
  (1, 'Demo Coffee', 1, 'https://example.com/img/shop1.jpg', '浦东新区', '上海市浦东新区世纪大道100号', 121.506377, 31.245105, 38, 260, 180, 48, '09:00-21:00', '2026-03-20 10:00:00', '2026-03-20 10:00:00'),
  (2, 'Hotpot Lab', 2, 'https://example.com/img/shop2.jpg', '静安区', '上海市静安区南京西路188号', 121.458123, 31.228456, 128, 430, 320, 46, '10:00-22:00', '2026-03-20 10:05:00', '2026-03-20 10:05:00'),
  (3, 'Fresh Bakery', 3, 'https://example.com/img/shop3.jpg', '黄浦区', '上海市黄浦区人民路66号', 121.490317, 31.222771, 26, 180, 95, 47, '07:30-20:30', '2026-03-20 10:10:00', '2026-03-20 10:10:00');

INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort`, `level`, `status`, `create_time`, `update_time`) VALUES
  (1, NULL, 'Food', 'food.png', 1, 1, 1, '2026-03-20 10:20:00', '2026-03-20 10:20:00'),
  (2, NULL, 'Drink', 'drink.png', 2, 1, 1, '2026-03-20 10:20:00', '2026-03-20 10:20:00'),
  (3, 1, 'Hotpot', 'hotpot.png', 1, 2, 1, '2026-03-20 10:21:00', '2026-03-20 10:21:00'),
  (4, 1, 'Bakery', 'bakery.png', 2, 2, 1, '2026-03-20 10:21:00', '2026-03-20 10:21:00'),
  (5, 2, 'Coffee', 'coffee.png', 1, 2, 1, '2026-03-20 10:21:00', '2026-03-20 10:21:00');

INSERT INTO `product` (`id`, `shop_id`, `category_id`, `name`, `sub_title`, `description`, `images`, `price`, `stock`, `status`, `create_time`, `update_time`) VALUES
  (1, 1, 5, 'Latte', 'Signature latte', 'Fresh milk with espresso', 'https://example.com/img/product1.jpg', 2800, 100, 1, '2026-03-20 11:00:00', '2026-03-20 11:00:00'),
  (2, 1, 5, 'Americano', 'Classic coffee', 'Double espresso with water', 'https://example.com/img/product2.jpg', 2200, 120, 1, '2026-03-20 11:01:00', '2026-03-20 11:01:00'),
  (3, 2, 3, 'Hotpot Combo', 'Best seller combo', 'Double person hotpot meal', 'https://example.com/img/product3.jpg', 16800, 50, 1, '2026-03-20 11:02:00', '2026-03-20 11:02:00'),
  (4, 3, 4, 'Butter Croissant', 'Daily baked', 'Crispy croissant with butter aroma', 'https://example.com/img/product4.jpg', 1200, 80, 1, '2026-03-20 11:03:00', '2026-03-20 11:03:00');

INSERT INTO `home_recommendation` (`id`, `biz_type`, `biz_id`, `title`, `sub_title`, `image`, `rank`, `status`, `start_time`, `end_time`, `create_time`, `update_time`) VALUES
  (1, 'SHOP', 1, 'Popular Coffee Shop', 'Good for work and meeting', 'https://example.com/img/home1.jpg', 100, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', '2026-03-20 11:30:00', '2026-03-20 11:30:00'),
  (2, 'PRODUCT', 3, 'Hotpot Combo', 'Top seller today', 'https://example.com/img/home2.jpg', 95, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', '2026-03-20 11:31:00', '2026-03-20 11:31:00'),
  (3, 'VOUCHER', 2, 'Seckill Voucher', 'Limited-time rush sale', 'https://example.com/img/home3.jpg', 90, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', '2026-03-20 11:32:00', '2026-03-20 11:32:00');

INSERT INTO `voucher` (`id`, `shop_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`, `create_time`, `update_time`) VALUES
  (1, 1, '咖啡5元代金券', '全场饮品可用', '每单限用1张, 不与其他优惠同享', 500, 1500, 0, 1, '2026-03-20 11:00:00', '2026-03-20 11:00:00'),
  (2, 1, '咖啡秒杀券', '限时秒杀, 先到先得', '每人限购1张, 仅限堂食', 100, 2000, 1, 1, '2026-03-20 11:05:00', '2026-03-20 11:05:00'),
  (3, 2, '火锅50元代金券', '满100可用', '周末不可用, 每桌限用1张', 5000, 10000, 0, 1, '2026-03-20 11:10:00', '2026-03-20 11:10:00'),
  (4, 2, '火锅秒杀券', '爆款套餐秒杀', '每人限购1张, 仅限晚市', 990, 5000, 1, 1, '2026-03-20 11:15:00', '2026-03-20 11:15:00'),
  (5, 3, '面包店新人券', '新人专享', '仅限首次到店消费', 300, 1200, 0, 1, '2026-03-20 11:20:00', '2026-03-20 11:20:00');

INSERT INTO `seckill_voucher` (`voucher_id`, `stock`, `begin_time`, `end_time`) VALUES
  (2, 20, '2026-01-01 00:00:00', '2027-12-31 23:59:59'),
  (4, 10, '2026-01-01 00:00:00', '2027-12-31 23:59:59');

INSERT INTO `voucher_order` (`id`, `user_id`, `voucher_id`, `status`, `order_no`, `create_time`, `update_time`, `pay_time`, `cancel_time`) VALUES
  (10001, 2, 1, 1, 'ORD202603200001', '2026-03-20 12:00:00', '2026-03-20 12:05:00', '2026-03-20 12:05:00', NULL),
  (10002, 3, 3, 2, 'ORD202603200002', '2026-03-20 12:10:00', '2026-03-20 12:40:00', NULL, '2026-03-20 12:40:00');

INSERT INTO `user_account` (`user_id`, `balance`, `frozen_balance`, `status`, `create_time`, `update_time`) VALUES
  (1, 500000, 0, 1, '2026-03-20 12:30:00', '2026-03-20 12:30:00'),
  (2, 200000, 0, 1, '2026-03-20 12:30:00', '2026-03-20 12:30:00'),
  (3, 100000, 0, 1, '2026-03-20 12:30:00', '2026-03-20 12:30:00');

SET FOREIGN_KEY_CHECKS = 1;
