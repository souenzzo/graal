{
  local common = import '../../common.jsonnet',
  local utils = import '../../common-utils.libsonnet',
  local config = import '../../repo-configuration.libsonnet',
  local linux_amd64 = common["linux-amd64"],

  local gate_math_stubs_listener = {
    name: 'daily-hotspot-mathstubs-listener-' + utils.prefixed_jdk(self.jdk_version),
    environment+: {
      "HOTSPOT_PORT_SYNC_CHECK" : "true"
    },
    setup+: [
      ["cd", "./" + config.compiler.compiler_suite],
    ],
    run+: [
      ["mx", "build"]
    ],
    timelimit : "10:00",
    notify_groups:: ["compiler_stubs"],
  },

  builds: [
    common.daily + linux_amd64 + common.oraclejdk8 + gate_math_stubs_listener,
  ]
}
