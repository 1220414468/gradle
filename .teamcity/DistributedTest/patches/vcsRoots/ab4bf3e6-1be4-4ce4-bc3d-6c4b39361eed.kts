package DistributedTest.patches.vcsRoots

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.*
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the vcsRoot with uuid = 'ab4bf3e6-1be4-4ce4-bc3d-6c4b39361eed' (id = 'DistributedTest_DistributedTest')
accordingly, and delete the patch script.
*/
changeVcsRoot(uuid("ab4bf3e6-1be4-4ce4-bc3d-6c4b39361eed")) {
    val expected = GitVcsRoot({
        uuid = "ab4bf3e6-1be4-4ce4-bc3d-6c4b39361eed"
        id("DistributedTest_DistributedTest")
        name = "DistributedTest"
        url = "https://github.com/gradle/gradle.git"
        branch = "refs/heads/blindpirate/distributed-test"
        authMethod = password {
            userName = "bot-teamcity"
            password = "%github.bot-teamcity.token%"
        }
    })

    check(this == expected) {
        "Unexpected VCS root settings"
    }

    (this as GitVcsRoot).apply {
        authMethod = password {
            userName = "bot-teamcity"
            password = "credentialsJSON:ecc6ec89-b940-4699-a466-cec87f0285da"
        }
    }

}
