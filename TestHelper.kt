package dsl.master.outbound.interfaces.siuout

import com.varian.fhir.common.Stu3ContextHelper
import com.varian.mappercore.framework.scripting.IScripts
import com.varian.mappercore.framework.scripting.ScriptFactory

class TestHelper {
    companion object {
        @JvmStatic



        private val parser = Stu3ContextHelper.forR4().newJsonParser()
        @JvmStatic
        var scripts: IScripts = ScriptFactory(
            "SIU_Out", mapOf(
                Pair(
                    "SIU_Out",
                    listOf(
                        "dsl/master/outbound/interfaces/siuout",
                        "dsl/master/outbound/common",
                        "dsl/master/outbound/helper"
                    )
                ),

            ), "ac_production"
        ).scripts
        fun readResource(path: String): String {
            var a=path
            return TestHelper::class.java.getResource(path).readText()//checking (in getResource )Path present or not
        }
    }//end of companion object

}