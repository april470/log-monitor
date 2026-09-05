-- 示例数据：接口日志
-- 插入一些模拟的接口调用记录，方便演示页面效果

INSERT INTO interface_log (trace_id, interface_name, method, request_params, response_data, cost_time, status, error_msg, create_time, caller_ip, env) VALUES
('TRC-20260822-001', 'com.example.logmonitor.controller.UserController', 'getUserList', '{"page":1,"size":10}', '{"code":0,"data":[{\"id\":1,\"name\":\"张三\"},{\"id\":2,\"name\":\"李四\"}]}', 125, 'SUCCESS', NULL, '2026-08-22 10:15:23', '192.168.1.101', 'prod'),
('TRC-20260822-002', 'com.example.logmonitor.controller.OrderController', 'createOrder', '{"userId":1,"productId":100,"amount":99.9}', '{"code":0,"orderId":20260822001}', 342, 'SUCCESS', NULL, '2026-08-22 10:16:45', '192.168.1.102', 'prod'),
('TRC-20260822-003', 'com.example.logmonitor.controller.UserController', 'login', '{"username":"admin","password":"***"}', '{"code":0,"token":"eyJhbGciOi..."}', 89, 'SUCCESS', NULL, '2026-08-22 10:17:12', '192.168.1.103', 'prod'),
('TRC-20260822-004', 'com.example.logmonitor.controller.PaymentController', 'pay', '{"orderId":20260822001,"channel":"alipay"}', NULL, 5234, 'ERROR', '支付超时：第三方支付接口响应超过5秒', '2026-08-22 10:18:30', '192.168.1.102', 'prod'),
('TRC-20260822-005', 'com.example.logmonitor.controller.InventoryController', 'deductStock', '{"productId":100,"quantity":5}', '{"code":0,"remainStock":95}', 2341, 'SUCCESS', NULL, '2026-08-22 10:19:05', '192.168.1.104', 'prod'),
('TRC-20260822-006', 'com.example.logmonitor.controller.UserController', 'getUserList', '{"page":2,"size":10}', '{"code":0,"data":[{\"id\":3,\"name\":\"王五\"}]}', 98, 'SUCCESS', NULL, '2026-08-22 10:20:18', '192.168.1.101', 'test'),
('TRC-20260822-007', 'com.example.logmonitor.controller.OrderController', 'cancelOrder', '{"orderId":20260822002}', NULL, 567, 'ERROR', '订单不存在或已取消，无法取消', '2026-08-22 10:21:42', '192.168.1.105', 'prod'),
('TRC-20260822-008', 'com.example.logmonitor.controller.ReportController', 'getDailyReport', '{"date":"2026-08-21"}', '{"code":0,"report":{...}}', 2341, 'SUCCESS', NULL, '2026-08-22 10:22:55', '192.168.1.106', 'prod'),
('TRC-20260822-009', 'com.example.logmonitor.controller.AuthController', 'refreshToken', '{"refreshToken":"eyJhbGciOi..."}', '{"code":401,"msg":"Token已过期"}', 45, 'ERROR', 'Token过期或无效，请重新登录', '2026-08-22 10:23:30', '192.168.1.103', 'dev'),
('TRC-20260822-010', 'com.example.logmonitor.controller.UserController', 'updateUser', '{"userId":1,"email":"zhangsan@test.com"}', '{"code":0,"msg":"更新成功"}', 156, 'SUCCESS', NULL, '2026-08-22 10:24:12', '192.168.1.101', 'prod'),
('TRC-20260822-011', 'com.example.logmonitor.controller.SearchController', 'search', '{"keyword":"手机","page":1}', '{"code":0,"total":234}', 1823, 'SUCCESS', NULL, '2026-08-22 10:25:48', '192.168.1.107', 'prod'),
('TRC-20260822-012', 'com.example.logmonitor.controller.PaymentController', 'refund', '{"orderId":20260822003,"reason":"用户申请"}', NULL, 8901, 'ERROR', '退款失败：原订单支付渠道不支持退款', '2026-08-22 10:26:55', '192.168.1.102', 'prod'),
('TRC-20260822-013', 'com.example.logmonitor.controller.UserController', 'getUserList', '{"page":1,"size":50}', '{"code":0,"data":[...]}', 456, 'SUCCESS', NULL, '2026-08-22 10:27:30', '192.168.1.108', 'test'),
('TRC-20260822-014', 'com.example.logmonitor.controller.CartController', 'addItem', '{"userId":1,"productId":200,"quantity":2}', '{"code":0,"cartId":12345}', 234, 'SUCCESS', NULL, '2026-08-22 10:28:15', '192.168.1.101', 'prod'),
('TRC-20260822-015', 'com.example.logmonitor.controller.OrderController', 'createOrder', '{"userId":99,"productId":999,"amount":9999}', NULL, 3456, 'ERROR', '库存不足：产品999仅剩0件', '2026-08-22 10:29:42', '192.168.1.109', 'prod');

-- 最近1小时的实时请求
INSERT INTO interface_log (trace_id, interface_name, method, request_params, response_data, cost_time, status, error_msg, create_time, caller_ip, env) VALUES
('TRC-20260822-016', 'com.example.logmonitor.controller.UserController', 'getUserInfo', '{"userId":1}', '{"code":0,"user":{...}}', 67, 'SUCCESS', NULL, NOW(), '192.168.1.101', 'prod'),
('TRC-20260822-017', 'com.example.logmonitor.controller.OrderController', 'getOrderList', '{"userId":1,"page":1}', '{"code":0,"orders":[...]}', 234, 'SUCCESS', NULL, NOW(), '192.168.1.101', 'prod'),
('TRC-20260822-018', 'com.example.logmonitor.controller.NotificationController', 'send', '{"type":"email","to":"user@test.com"}', '{"code":0,"sent":true}', 567, 'SUCCESS', NULL, NOW(), '192.168.1.110', 'prod'),
('TRC-20260822-019', 'com.example.logmonitor.controller.UploadController', 'upload', '{"fileName":"report.pdf","size":1048576}', NULL, 2345, 'ERROR', '上传失败：文件大小超过限制(1MB)', NOW(), '192.168.1.111', 'dev'),
('TRC-20260822-020', 'com.example.logmonitor.controller.UserController', 'getUserList', '{"page":3,"size":10}', '{"code":0,"data":[...]}', 123, 'SUCCESS', NULL, NOW(), '192.168.1.102', 'prod');