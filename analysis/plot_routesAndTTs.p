input_file = "routesAndTTs.txt"
output_file = "routesAndTTs.pdf"


set terminal pdfcairo notransparent color solid size 7in,6in font "Bitstream, 14" 

set output output_file
set multiplot
set size 1, 0.25

#set key outside bottom center 
set key inside top right
#set key off
set key samplen 1

#start printing total travel times
set origin 0.0, 0.75
set ylabel 'total tt [s]'
plot input_file using 1:2 notitle with points ls 4 pt 11 ps 0.5,\
1/0 title "total tt" with points ls 4 pt 11 ps 1

#start printing avg route travel times
set origin 0.0,0.5
set ylabel 'avg tt [s]'
plot input_file using 1:6 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
input_file using 1:7 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
input_file using 1:8 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1

#start printing avg route travel times
set origin 0.0,0.25
set ylabel 'avg toll'
plot input_file using 1:9 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
input_file using 1:10 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
input_file using 1:11 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1

#start printing #users on routes
set origin 0.0,0.0
set xlabel 'iteration'
set ylabel '#agents'
plot input_file using 1:3 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
input_file using 1:4 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
input_file using 1:5 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1,\
input_file using 1:12 notitle with points ls 6 pt 13 ps 0.5,\
1/0 title "#stucked agents" with points ls 6 pt 13 ps 1

print "output written to: ".output_file

#unset multiplot