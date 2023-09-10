package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        //验证手机
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合
            return Result.fail("手机不符合格式");
        }
        //检查输入的验证码

        // String cacheCode = (String)session.getAttribute(phone);
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);


        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误或者手机号错误");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        //保存用户
        //session.setAttribute("user",user);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //todo 生成token
        String token = UUID.randomUUID().toString(true);

        //todo 将user转为map
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, Duration.ofSeconds(LOGIN_USER_TTL));
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(7));
        save(user);
        return user;
    }

    @Override
    public Result sendCode(String phone) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合

            return Result.fail("手机不符合格式");
        }
        //符合格式,生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码
        // session.setAttribute(phone,code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code);
        stringRedisTemplate.expire(LOGIN_CODE_KEY + phone, Duration.ofMinutes(LOGIN_CODE_TTL));
        //调用发送验证码接口
        System.out.println("code=" + code);

        return Result.ok();
    }
}
