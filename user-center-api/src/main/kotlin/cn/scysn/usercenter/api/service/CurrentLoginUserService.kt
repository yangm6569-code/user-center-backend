package cn.scysn.usercenter.api.service

import cn.scysn.usercenter.api.CurrentLoginUser

interface CurrentLoginUserService {
    fun current(): CurrentLoginUser?
}
