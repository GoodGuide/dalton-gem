# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)
require "dalton/version"

Gem::Specification.new do |s|
  s.name        = "dalton"
  s.version     = Dalton::VERSION
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Brian Jenkins", "Joshua Bates"]
  s.email       = ["brian@brianjenkins.org", "joshua@goodguide.com"]
  s.homepage    = ""
  s.summary     = %q{A thin Datomic driver for JRuby}
  s.description = %q{Dalton attempts to give low level access to Datomic from JRuby}

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]

  s.add_runtime_dependency 'lock_jar', '>=0.7.5'
  s.add_runtime_dependency 'zweikopf', '0.4.0'
  s.add_development_dependency 'rspec', '>=2.14.1'

  s.extensions = ["Rakefile"]
end
