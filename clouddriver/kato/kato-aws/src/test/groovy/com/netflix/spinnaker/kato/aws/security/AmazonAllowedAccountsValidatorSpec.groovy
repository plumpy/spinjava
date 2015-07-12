package com.netflix.spinnaker.kato.aws.security

import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.kato.aws.deploy.description.AllowLaunchDescription
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

class AmazonAllowedAccountsValidatorSpec extends Specification {
  NetflixAmazonCredentials credentialsWithRequiredGroup = new NetflixAmazonCredentials(
    "TestAccount", "TestAccount", null, null, ["targetAccount1", "targetAccount2"], null, null, null, null, null, null
  )

  @Unroll
  void "should reject if allowed accounts does not intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new AllowLaunchDescription()
    description.credentials = credentialsWithRequiredGroup

    validator.validate("TestAccount", allowedAccounts, description, errors)

    then:
    rejectValueCount * errors.rejectValue("credentials", "unauthorized", _)

    where:
    allowedAccounts                      || rejectValueCount
    []                                   || 1
    ["targetAccount1"]                   || 0
    ["targetAccount3"]                   || 1
    ["targetAccount1", "targetAccount2"] || 0
  }

  void "should allow if allow accounts intersect with required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider) {
      1 * getAll() >> { [credentialsWithRequiredGroup] }
      0 * _
    })

    when:
    def description = new AllowLaunchDescription()
    description.credentials = credentialsWithRequiredGroup

    validator.validate("TestAccount", [credentialsWithRequiredGroup.requiredGroupMembership[0]], description, errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }

  void "should allow if no required group memberships"() {
    given:
    def errors = Mock(Errors)
    def validator = new AmazonAllowedAccountsValidator(Mock(AccountCredentialsProvider))

    when:
    validator.validate("TestAccount", [], new AllowLaunchDescription(), errors)

    then:
    0 * errors.rejectValue(_, _, _)
  }
}
