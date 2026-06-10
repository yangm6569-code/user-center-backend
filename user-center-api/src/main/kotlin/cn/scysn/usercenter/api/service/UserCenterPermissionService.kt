package cn.scysn.usercenter.api.service

import cn.scysn.usercenter.api.UserCenterPermissionInfo

interface UserCenterPermissionService {
    fun effectivePermissions(userId: Long): List<UserCenterPermissionInfo>
}
