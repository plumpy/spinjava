/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.amos.aws.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.netflix.spinnaker.amos.aws.AWSAccountInfoLookup
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.AssumeRoleAmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import spock.lang.Specification
import com.netflix.spinnaker.amos.aws.config.CredentialsConfig.Region
import com.netflix.spinnaker.amos.aws.config.CredentialsConfig.Account

class CredentialsLoaderSpec extends Specification {

    def 'basic test with defaults'() {
        setup:
        def config = new CredentialsConfig(defaultRegions: [
                new Region(name: 'us-east-1', availabilityZones: ['us-east-1c', 'us-east-1d', 'us-east-1e']),
                new Region(name: 'us-west-2', availabilityZones: ['us-west-2a', 'us-west-2b'])],
                defaultKeyPairTemplate: 'nf-{{name}}-keypair-a',
                defaultEddaTemplate: 'http://edda-main.%s.{{name}}.netflix.net',
                defaultFront50Template: 'http://front50.prod.netflix.net/{{name}}',
                defaultDiscoveryTemplate: 'http://%s.discovery{{name}}.netflix.net',
                defaultAssumeRole: 'role/asgard',
                accounts: [
                        new Account(name: 'test', accountId: 12345),
                        new Account(name: 'prod', accountId: 67890)
                ]
        )
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<AmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, AmazonCredentials)

        when:
        List<AmazonCredentials> creds = ci.load(config)

        then:
        creds.size() == 2
        with(creds.find { it.name == 'prod' }) { AmazonCredentials cred ->
            cred.accountId == 67890
            cred.defaultKeyPair == 'nf-prod-keypair-a'
            cred.regions.size() == 2
            cred.regions.find { it.name == 'us-east-1' }.availabilityZones.size() == 3
            cred.regions.find { it.name == 'us-west-2' }.availabilityZones.size() == 2
            cred.credentialsProvider == provider
        }
        0 * _
    }

    def 'account resolves defaults'() {
        setup:
        def config = new CredentialsConfig(accounts: [new Account(name: 'default')])
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        def ci = new CredentialsLoader<AmazonCredentials>(provider, lookup, AmazonCredentials)

        when:
        List<AmazonCredentials> creds = ci.load(config)

        then:
        1 * lookup.findAccountId() >> 696969
        1 * lookup.listRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a', 'us-east-1b'])]
        creds.size() == 1
        with (creds.first()) { AmazonCredentials cred ->
            cred.name == 'default'
            cred.accountId == 696969
            cred.credentialsProvider == provider
            cred.defaultKeyPair == null
            cred.regions.size() == 1
            cred.regions.first().name == 'us-east-1'
            cred.regions.first().availabilityZones.toList().sort() == ['us-east-1a', 'us-east-1b']
        }
        0 * _
    }

    def 'availibilityZones are resolved in default regions only once'() {
        setup:
        def config = new CredentialsConfig(defaultRegions: [new Region(name: 'us-east-1'), new Region(name: 'us-west-2')], accounts: [new Account(name: 'default', accountId: 1), new Account(name: 'other', accountId: 2)])
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<AmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, AmazonCredentials)

        when:
        List<AmazonCredentials> creds = ci.load(config)

        then:
        1 * lookup.listRegions(['us-east-1', 'us-west-2']) >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a']), new AmazonCredentials.AWSRegion('us-west-2', ['us-west-2a'])]
        creds.size() == 2
        with (creds.find { it.name == 'default' }) { AmazonCredentials cred ->
            cred.regions.size() == 2
            cred.regions.toList().sort { it.name }.name == ['us-east-1', 'us-west-2']
        }
        0 * _
    }

    def 'availabilityZones are resolved for account-specific region if not defined in defaults'() {
        def config = new CredentialsConfig(
                defaultRegions: [new Region(name: 'us-east-1')],
                accounts: [
                        new Account(
                                name: 'default',
                                accountId: 1,
                                regions: [ new Region(name: 'us-west-2')])])
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<AmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, AmazonCredentials)

        when:
        List<AmazonCredentials> creds = ci.load(config)

        then:
        1 * lookup.listRegions(['us-east-1']) >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a'])]
        1 * lookup.listRegions(['us-west-2']) >> [new AmazonCredentials.AWSRegion('us-west-2', ['us-west-2a'])]
        creds.size() == 1
        with(creds.first()) { AmazonCredentials cred ->
            cred.regions.size() == 1
            cred.regions.first().name == 'us-west-2'
            cred.regions.first().availabilityZones == ['us-west-2a']
        }
    }

    def 'account overrides defaults'() {
        setup:
        def config = new CredentialsConfig(defaultRegions: [
                new Region(name: 'us-east-1', availabilityZones: ['us-east-1c', 'us-east-1d', 'us-east-1e']),
                new Region(name: 'us-west-2', availabilityZones: ['us-west-2a', 'us-west-2b'])],
                defaultKeyPairTemplate: 'nf-{{name}}-keypair-a',
                defaultEddaTemplate: 'http://edda-main.%s.{{name}}.netflix.net',
                defaultFront50Template: 'http://front50.prod.netflix.net/{{name}}',
                defaultDiscoveryTemplate: 'http://%s.discovery{{name}}.netflix.net',
                defaultAssumeRole: 'role/asgard',
                accounts: [
                        new Account(
                                name: 'test',
                                accountId: 12345,
                                regions: [new Region(name: 'us-west-1', availabilityZones: ['us-west-1a'])],
                                discovery: 'us-west-1.discoveryqa.netflix.net',
                                eddaEnabled: false,
                                defaultKeyPair: 'oss-{{accountId}}-keypair')
                ]
        )
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<NetflixAmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, NetflixAmazonCredentials)

        when:
        List<NetflixAmazonCredentials> creds = ci.load(config)

        then:
        creds.size() == 1
        with(creds.first()) { NetflixAmazonCredentials cred ->
            cred.name == 'test'
            cred.accountId == 12345
            cred.defaultKeyPair == 'oss-12345-keypair'
            cred.discovery == 'us-west-1.discoveryqa.netflix.net'
            cred.discoveryEnabled
            cred.edda == 'http://edda-main.%s.test.netflix.net'
            !cred.eddaEnabled
            cred.front50 == 'http://front50.prod.netflix.net/test'
            cred.front50Enabled
            cred.regions.size() == 1
            cred.regions.first().name == 'us-west-1'
            cred.regions.first().availabilityZones == ['us-west-1a']
            cred.credentialsProvider == provider
        }
        0 * _
    }

    def 'create single default account'() {
        setup:
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<NetflixAmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, NetflixAmazonCredentials)

        when:
        NetflixAmazonCredentials cred = ci.load('default')

        then:
        1 * lookup.findAccountId() >> 12345
        1 * lookup.listRegions() >> [new AmazonCredentials.AWSRegion('us-east-1', ['us-east-1a', 'us-east-1b'])]
        cred.name == 'default'
        cred.regions.size() == 1
        cred.regions.first().name == 'us-east-1'
        cred.regions.first().availabilityZones.toList().sort() == ['us-east-1a', 'us-east-1b']
        !cred.discoveryEnabled
        !cred.eddaEnabled
        !cred.front50Enabled
    }

    def 'accountId must be provided for assumeRole account types'() {
        setup:
        def config = new CredentialsConfig(
                defaultRegions: [new Region(name: 'us-east-1', availabilityZones: ['us-east-1a'])],
                accounts: [new Account(name: 'gonnaFail')])
        AWSCredentialsProvider provider = Mock(AWSCredentialsProvider)
        AWSAccountInfoLookup lookup = Mock(AWSAccountInfoLookup)
        CredentialsLoader<AssumeRoleAmazonCredentials> ci = new CredentialsLoader<>(provider, lookup, AssumeRoleAmazonCredentials)

        when:
        ci.load(config)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.getMessage().startsWith'accountId is required'
        0 * _
    }
}
