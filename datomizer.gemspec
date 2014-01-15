# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)
require "datomizer/version"

Gem::Specification.new do |s|
  s.name        = "datomizer"
  s.version     = Datomizer::VERSION
  s.platform    = Gem::Platform::RUBY
  s.authors     = ["Brian Jenkins", "Joshua Bates"]
  s.email       = ["brian@brianjenkins.org", "joshua@goodguide.com"]
  s.homepage    = ""
  s.summary     = %q{A thin Datomic driver for JRuby}
  s.description = %q{Datomizer attempts to give low level access to Datomic from JRuby}

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]

  s.add_runtime_dependency 'lock_jar', '>=0.7.5'
  s.add_runtime_dependency 'zweikopf', '>=0.0.6'

  s.extensions = ["Rakefile"]
end
