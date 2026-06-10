package cn.scysn.usercenter.application.api

import cn.scysn.usercenter.api.CurrentLoginUser
import cn.scysn.usercenter.api.service.CurrentLoginUserService
import cn.scysn.usercenter.application.AuthorizationApplicationService
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserAccountJpaRepository
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CurrentLoginUserServiceImpl(
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val authorizationApplicationService: AuthorizationApplicationService,
) : CurrentLoginUserService {
    @Transactional(readOnly = true)
    override fun current(): CurrentLoginUser? {
        val principal = runCatching { currentLoginPrincipal() }.getOrNull() ?: return null
        val user = userAccountJpaRepository.findById(principal.userId).orElse(null) ?: return null
        val roles = authorizationApplicationService.effectivePermission(principal.userId).roles.map { it.code }
        return CurrentLoginUser(
            id = user.id ?: 0,
            username = user.username,
            displayName = user.displayName,
            roles = roles,
        )
    }
}
