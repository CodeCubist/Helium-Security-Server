package dev.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 将不同类型的日期对象格式化为指定格式的字符串
     * @param dateObj 日期对象（可以是String、Long或Date类型）
     * @return 格式化后的日期字符串
     */
    public static String formatDate(Object dateObj) {
        if (dateObj == null) {
            return "-";
        }
        
        Date date = null;
        
        if (dateObj instanceof String) {
            
            return (String) dateObj;
        } else if (dateObj instanceof Long) {
            
            date = new Date((Long) dateObj);
        } else if (dateObj instanceof Date) {
            
            date = (Date) dateObj;
        } else {
            
            try {
                date = DATE_FORMAT.parse(dateObj.toString());
            } catch (ParseException e) {
                return "-";
            }
        }
        
        return date != null ? DATE_FORMAT.format(date) : "-";
    }
}