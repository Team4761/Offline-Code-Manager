set oldDir=%CD%
%1:
cd %2
git log > %oldDir%\inf\commit_%1.info
