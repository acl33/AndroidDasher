#!/usr/bin/python

import sys
import re

if len(sys.argv)!=2:
  print "Usage: python xml.py /path/to/dasher_strings.csv"
  print " Expects a two-column csv file, i.e. internal names and one language's worth"
  print " of translations. Outputs bulk of XML, but some changes needed by hand"
  sys.exit(1)

def dequote(s):
  s=s.strip()
  if (s==""): return s
  if (s[0]=='"' and s[-1]=='"'): return s[1:-1]
  raise Error("Couldn't dequote "+s)

with open(sys.argv[1],'r') as file:
  for line in file:
    i = line.find(',')
    if i==-1:
      raise Error("No comma in "+line)
    key=line[:i]
    s=dequote(line[i+1:])
    if (s==""):
      print "<!--",dequote(key),"-->"
    elif (key==""):
      print "<item>"+s+"</item>"
    else:
      print "<string name="+key+">"+s+"</string>"
