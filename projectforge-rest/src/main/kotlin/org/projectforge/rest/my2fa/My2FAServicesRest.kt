/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2022 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest.my2fa

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import mu.KotlinLogging
import org.apache.commons.codec.binary.Base64
import org.projectforge.business.user.filter.CookieService
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.api.UserContext
import org.projectforge.framework.utils.NumberHelper
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.core.SessionCsrfService
import org.projectforge.rest.dto.PostData
import org.projectforge.rest.dto.ServerData
import org.projectforge.rest.pub.LoginPageRest
import org.projectforge.rest.pub.My2FAPublicServicesRest
import org.projectforge.security.My2FAData
import org.projectforge.security.My2FAService
import org.projectforge.security.OTPCheckResult
import org.projectforge.security.WebAuthnServicesRest
import org.projectforge.security.dto.WebAuthnFinishRequest
import org.projectforge.security.webauthn.WebAuthnSupport
import org.projectforge.ui.*
import org.projectforge.web.My2FAHttpService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

private val log = KotlinLogging.logger {}

/**
 * User may setup his/her 2FA and may check this. 2FA is usable via Authenticator apps (Microsoft or Google Authenticator),
 * via texting messages (if sms is configured) or via e-mails as a fall back.
 */
@RestController
@RequestMapping("${Rest.URL}/2FA")
class My2FAServicesRest {
  @Autowired
  private lateinit var cookieService: CookieService

  @Autowired
  private lateinit var sessionCsrfService: SessionCsrfService

  @Autowired
  private lateinit var my2FAService: My2FAService

  @Autowired
  private lateinit var my2FAHttpService: My2FAHttpService

  @Autowired
  private lateinit var webAuthnSupport: WebAuthnSupport

  @Autowired
  private lateinit var webAuthnServicesRest: WebAuthnServicesRest

  /**
   * For validating the Authenticator's OTP, or OTP sent by sms or e-mail.
   * @param redirect Is used by OTP check after login.
   * @param afterLogin Used by [org.projectforge.rest.pub.My2FAPublicServicesRest]. If true, no toast should be
   *                   created, a CHECK_AUTHENTICATION should be returned.
   */
  @PostMapping("checkOTP")
  fun checkOTP(
    request: HttpServletRequest,
    response: HttpServletResponse,
    @Valid @RequestBody postData: PostData<My2FAData>,
    @RequestParam("redirect", required = false) redirect: String?,
    afterLogin: Boolean = false,
  ): ResponseEntity<ResponseAction> {
    sessionCsrfService.validateCsrfToken(request, postData)?.let { return it }
    val data = postData.data
    val otp = data.code
    val password = data.password
    val otpCheck = internalCheckOTP(request, response, otp, password)
    if (otpCheck == OTPCheckResult.CODE_EMPTY) {
      return UIToast.createToastResponseEntity(translate("user.My2FA.setup.check.fail"), color = UIColor.DANGER)
    }
    if (otpCheck == OTPCheckResult.SUCCESS) {
      if (afterLogin) {
        val redirectUrl = LoginPageRest.getRedirectUrl(request, postData.serverData)
        return ResponseEntity(
          ResponseAction(targetType = TargetType.CHECK_AUTHENTICATION, url = redirectUrl),
          HttpStatus.OK
        )
      }
      if (data.target != null) {
        val redirectUrl = String(Base64.decodeBase64(data.target), StandardCharsets.UTF_8)
        if (redirectUrl.isNotBlank()) {
          return ResponseEntity(
            ResponseAction(targetType = TargetType.REDIRECT, url = redirectUrl),
            HttpStatus.OK
          )
        }
      }
      data.code = ""
      data.lastSuccessful2FA = My2FAService.getLastSuccessful2FAAsTimeAgo()
      return UIToast.createToastResponseEntity(
        translate("user.My2FA.setup.check.success"),
        color = UIColor.SUCCESS,
        mutableMapOf("data" to postData.data),
        merge = true,
        targetType = TargetType.UPDATE
      )
    }
    // otp check wasn't successful:
    otpCheck.userMessage?.let { msg ->
      return AbstractDynamicPageRest.showValidationErrors(
        ValidationError(msg)
      )
    }
    if (otpCheck == OTPCheckResult.WRONG_LOGIN_PASSWORD) {
      return AbstractDynamicPageRest.showValidationErrors(
        ValidationError(
          translate("user.My2FACode.password.wrong"),
          "password"
        )
      )
    }
    return AbstractDynamicPageRest.showValidationErrors(
      ValidationError(
        translate("user.My2FACode.error.validation"),
        "code"
      )
    )
  }

  /**
   * Checks the given otp and stores it as cookie and in the user's current session on success.
   */
  fun internalCheckOTP(
    request: HttpServletRequest,
    response: HttpServletResponse,
    otp: String?,
    password: CharArray? = null
  ): OTPCheckResult {
    val otpCheck = my2FAHttpService.checkOTP(request, code = otp, password = password)
    if (otpCheck == OTPCheckResult.SUCCESS) {
      updateCookieAndSession(request, response)
    }
    return otpCheck
  }

  /**
   * Should be called after [ThreadLocalUserContext.getUserContext] -> [UserContext.updateLastSuccessful2FA].
   * Updates the time stamp of the last succesful 2FA as cookie as well as in the user's session.
   */
  fun updateCookieAndSession(request: HttpServletRequest, response: HttpServletResponse) {
    ThreadLocalUserContext.getUserContext().lastSuccessful2FA?.let { lastSuccessful2FA ->
      cookieService.addLast2FACookie(request, response, lastSuccessful2FA)
      // Store it also in the user's session, e. g. used by public password reset service.
      request.getSession(false)?.setAttribute(SESSION_KEY_LAST_SUCCESSFUL_2FA, lastSuccessful2FA)
    }
  }

  /**
   * Sends a OTP as code (text to mobile phone of logged-in user).
   */
  @GetMapping("sendSmsCode")
  fun sendSmsCode(request: HttpServletRequest): ResponseEntity<*> {
    val user = ThreadLocalUserContext.getUser()
    val mobilePhone = user.mobilePhone
    if (mobilePhone == null) {
      log.error { "User '${user.username}' tried to send 2FA code as text message, but mobile phone isn't available." }
      return ResponseEntity<Any>(HttpStatus.BAD_REQUEST)
    }
    if (!my2FAService.smsConfigured) {
      log.error { "User tried to send a text message, but sms isn't configured." }
      return ResponseEntity<Any>(HttpStatus.BAD_REQUEST)
    }
    val result = my2FAHttpService.createAndTextOTP(request, mobilePhone)
    return createResponseEntity(result)
  }


  /**
   * Sends a OTP as code vial mail.
   */
  @GetMapping("sendMailCode")
  fun sendMailCode(request: HttpServletRequest): ResponseEntity<*> {
    val user = ThreadLocalUserContext.getUser()
    if (user.email.isNullOrBlank()) {
      log.error { "User '${user.username}' tried to send 2FA code as mail, but e-mail address isn't available." }
      return ResponseEntity<Any>(HttpStatus.BAD_REQUEST)
    }
    val result = my2FAHttpService.createAndMailOTP(request)
    return createResponseEntity(result)
  }

  @PostMapping("authenticateFinish")
  fun authenticateFinish(
    request: HttpServletRequest,
    httpResponse: HttpServletResponse,
    @RequestBody postData: PostData<My2FAWebAuthnData>
  ): ResponseEntity<ResponseAction> {
    val webAuthnFinishRequest = postData.data.webAuthnFinishRequest
    if (postData.serverData == null) {
      // Server data is not sent by client (not yet implemented), so this is a work arround:
      postData.serverData = ServerData(postData.data.csrfToken)
    }
    sessionCsrfService.validateCsrfToken(request, postData)?.let { return it }
    requireNotNull(webAuthnFinishRequest)
    val result = webAuthnServicesRest.doAuthenticateFinish(request, httpResponse, webAuthnFinishRequest)
    if (result.success) {
      return UIToast.createToastResponseEntity(
        translate("user.My2FA.setup.check.success"),
        color = UIColor.SUCCESS,
        mutableMapOf("data" to postData.data),
        merge = true,
        targetType = TargetType.UPDATE
      )
    }
    // Authentication wasn't successful:
    result.errorMessage!!.let { msg ->
      return UIToast.createToastResponseEntity(
        translate(msg),
        color = UIColor.DANGER,
      )
    }
  }

  fun fill2FA(request: HttpServletRequest, layout: UILayout, my2FAData: My2FAData, redirectUrl: String? = null) {
    val fieldset = UIFieldset(12, title = "user.My2FACode.title")
    layout.add(fieldset)
    fill2FA(request, fieldset, my2FAData, redirectUrl)
  }

  fun fill2FA(request: HttpServletRequest, col: UICol, my2FAData: My2FAData, redirectUrl: String? = null) {
    val row = UIRow()
    col.add(row)
    fill2FA(request, row, my2FAData, redirectUrl)
  }

  private fun fill2FA(request: HttpServletRequest, row: UIRow, my2FAData: My2FAData, redirectUrl: String? = null) {
    my2FAData.lastSuccessful2FA = My2FAService.getLastSuccessful2FAAsTimeAgo()
    val codeCol = UICol(md = 6)
    row.add(codeCol)
    fillCodeCol(request, codeCol, redirectUrl, restServiceClass = My2FAServicesRest::class.java)
    //val showPasswordCol = my2FARequestConfiguration.checkLoginPasswordRequired4Mail2FA()
    /*
      // Enable E-Mail with password (required for security reasons, if attacker has access to local client)
      val passwordCol = UICol(md = width)
      row.add(passwordCol)
      passwordCol.add(
        UIInput(
          "password",
          label = "password",
          tooltip = "user.My2FACode.password.info",
          dataType = UIDataType.PASSWORD,
          autoComplete = UIInput.AutoCompleteType.OFF
        )
      )*/

    val last2FACol = UICol(md = 6)
    row.add(last2FACol)
    last2FACol.add(UIReadOnlyField("lastSuccessful2FA", label = "user.My2FACode.lastSuccessful2FA"))
  }

  /**
   * A 2FA is needed for any public page (login or password reset).
   * @param mailOTPDisabled Should be disabled for password reset (the reset link was also sent by mail, so it's not really a 2FA.
   * @param restServiceClass Class with services such as checkOTP, mailCode (if configured) and sendSmsCode.
   */
  fun fillLayout4PublicPage(
    request: HttpServletRequest,
    layout: UILayout,
    userContext: UserContext,
    restServiceClass: Class<*> = My2FAPublicServicesRest::class.java,
    redirectUrl: String? = null,
    mailOTPDisabled: Boolean = false,
  ) {
    val fieldset = UIFieldset(12, title = "user.My2FACode.title")
    layout.add(fieldset)
    fieldset.add(
      UIAlert(
        message = "user.My2FACode.authentification.info",
        markdown = true,
        color = UIColor.INFO
      )
    )
    fillCodeCol(
      request,
      fieldset,
      redirectUrl,
      userContext.user?.mobilePhone,
      showCancelButton = true,
      mailOTPDisabled,
      restServiceClass,
      userContext,
    )
  }

  /**
   * @param showCancelButton Only true for login process (the private services aren't yet available) and password reset
   * @param mailOTPDisabled True, if no mail button should be displayed (escpecially for password reset).
   * @param restServiceClass Optional rest service class, [My2FAPublicServicesRest] is default.
   */
  private fun fillCodeCol(
    request: HttpServletRequest,
    codeCol: UICol,
    redirectUrl: String? = null,
    mobilePhone: String? = ThreadLocalUserContext.getUser()?.mobilePhone,
    showCancelButton: Boolean = false,
    mailOTPDisabled: Boolean = false,
    restServiceClass: Class<*>,
    userContext: UserContext? = null,
  ) {
    val smsAvailable = my2FAHttpService.smsConfigured && NumberHelper.matchesPhoneNumber(mobilePhone)
    codeCol
      .add(
        UIInput(
          "code", label = "user.My2FACode.code", tooltip = "user.My2FACode.code.info",
          autoComplete = UIInput.AutoCompleteType.OFF,
          focus = true,
        )
      )
    if (showCancelButton) {
      codeCol.add(
        UIButton.createCancelButton(
          responseAction = ResponseAction(
            RestResolver.getRestUrl(restServiceClass, "cancel"),
            targetType = TargetType.GET
          ),
        )
      )
    }
    codeCol.add(
      UIButton.createDefaultButton(
        "validate",
        title = "user.My2FACode.code.validate",
        responseAction = ResponseAction(
          RestResolver.getRestUrl(restServiceClass, "checkOTP", params = mapOf("redirect" to redirectUrl)),
          targetType = TargetType.POST
        ),
      )
    )
    if (webAuthnSupport.isAvailableForUser(userContext?.user?.id)) {
      codeCol.add(
        UICustomized(
          "webauthn.authenticate",
          mutableMapOf(
            "authenticateFinishUrl" to RestResolver.getRestUrl(this::class.java, "authenticateFinish"),
            "csrfToken" to sessionCsrfService.ensureAndGetToken(request)
          )
        )
      )
    }
    if (smsAvailable) {
      codeCol.add(createSendButton(My2FAType.SMS, restServiceClass))
    }
    if (!mailOTPDisabled) {
      codeCol.add(createSendButton(My2FAType.MAIL, restServiceClass))
    }
  }

  /**
   * @param type [My2FAType.MAIL] and [My2FAType.SMS] supported.
   * @param usePublicServices Only true for login process (the private services aren't yet available.
   */
  private fun createSendButton(type: My2FAType, restServiceClass: Class<*> = this::class.java): UIButton {
    val lowerType = type.name.lowercase()
    val type = lowerType.replaceFirstChar { it.uppercase() }
    return UIButton.createLinkButton(
      "send${type}Code",
      title = "user.My2FACode.sendCode.$lowerType", // user.My2FACode.sendCode.mail
      tooltip = "user.My2FACode.sendCode.$lowerType.info",
      responseAction = ResponseAction(
        RestResolver.getRestUrl(restServiceClass, "send${type}Code"),
        targetType = TargetType.GET
      ),
    )
  }

  companion object {
    private val SESSION_KEY_LAST_SUCCESSFUL_2FA = "${My2FAServicesRest::class.java.name}.lastSuccessFul2FA"

    /**
     * Gets the last successful 2FA from the user's session.
     */
    fun getLastSuccessful2FAFromSession(request: HttpServletRequest): Long? {
      return request.getSession(false)?.getAttribute(SESSION_KEY_LAST_SUCCESSFUL_2FA) as? Long
    }

    internal fun createResponseEntity(result: My2FAHttpService.Result): ResponseEntity<ResponseAction> {
      val color = if (result.success) {
        UIColor.SUCCESS
      } else {
        UIColor.DANGER
      }
      return UIToast.createToastResponseEntity(result.message, color = color)
    }
  }

  /**
   * Only used for [authenticateFinish].
   */
  @JsonIgnoreProperties(ignoreUnknown = true) // mobile phone etc. of setup data must be ignored.
  class My2FAWebAuthnData : My2FAData() {
    var webAuthnFinishRequest: WebAuthnFinishRequest? = null
    var csrfToken: String? = null // Can't get server data in WebAuthenticate.jsx
  }
}