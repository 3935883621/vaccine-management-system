const b = require('bcryptjs');
// 生成 $2a$ 前缀的 BCrypt 哈希（密码 123456），与 Spring BCryptPasswordEncoder 兼容
const h = '$2b$10$P794GrCoyBB3j2vNbdjRv.lkyE4q1fG03CpMyKZdj1WOZmjAB5WPy'.replace('2b', '2a');
console.log('HASH=' + h);
console.log('VERIFY=' + b.compareSync('123456', h));
