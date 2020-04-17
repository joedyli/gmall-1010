package com.atguigu.gmall.common.exception;

public class UserException extends RuntimeException {

    private String msg;

    public UserException() {
        super();
    }

    public UserException(String msg){
        super(msg);
    }
}
