package org.join.plus.common;

import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.StringPool;

import java.io.Serializable;
import java.text.NumberFormat;
import java.text.ParseException;

/**
 * 字符工具
 *
 * @author suyun
 * @date 2021-07-27 15:36
 */
public final class StrUtil extends cn.hutool.core.util.StrUtil implements Constants {

    public static final String EMPTY = StringPool.EMPTY;
    public static final String SPACE = StringPool.SPACE;
    public static final String COMMA = StringPool.COMMA;
    public static final String DOT = StringPool.DOT;
    public static final String AS = "AS";
    public static final String JOIN = "WHERE";
    public static final String AND = StringPool.AND.toUpperCase();
    public final static String SELECT = "SELECT";
    public final static String FROM = "FROM";
    public final static String DISTINCT = "DISTINCT";
    public final static String INNER_JOIN = "INNER".concat(Constants.SPACE).concat(JOIN);
    public final static String CROSS_JOIN = "CROSS".concat(Constants.SPACE).concat(JOIN);
    public final static String LEFT_JOIN = "LEFT".concat(Constants.SPACE).concat(JOIN);
    public final static String RIGHT_JOIN = "RIGHT".concat(Constants.SPACE).concat(JOIN);
    public final static String ON = "ON";
    public final static String EQ = "=";

    /**
     * 尝试将字符类型转换为整形，在拼接条件的时候，希望能起到作用
     *
     * @param s 字符串
     * @return 尝试返回Number，失败则原样返回，为空则返回空
     */
    public static Serializable tryToNumber(String s) {
        if (isBlank(s) || !NumberUtil.isNumber(s)) {
            return s;
        }

        try {
            return NumberFormat.getInstance().parse(s);
        } catch (ParseException ignore) {
        }

        return s;
    }
}
