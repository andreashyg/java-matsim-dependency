#file_onRoutes = "onRoutes.txt"
file_summedDepartures = "summedDeparturesPerRoute.txt"
#file_routeStarts = "startsPerRoute.txt"
file_avgRouteTTsByDepartureTime = "avgRouteTTsPerDeparture.txt"
file_avgRouteTollsByDepartureTime = "avgRouteTollsPerDeparture.txt"

output_file = "routeDistribution.pdf"


set terminal pdfcairo notransparent color solid size 7in,6in font "Bitstream, 18" 

set output output_file
set multiplot
set size 1, 0.33

#set key outside bottom center 
set key inside bottom right
#set key off
set key samplen 1

#start printing #agents on routes
#set origin 0.0, 0.5
#set ylabel '#agents'
#stats file_onRoutes u 1
#set xrange [STATS_min:STATS_max]
#plot file_onRoutes using 1:2 title "#agents on route 1" with linespoints ps 0.5,\
#file_onRoutes using 1:3 title "#agents on route 2" with linespoints ps 0.5,\
#file_onRoutes using 1:4 title "#agents on route 3" with linespoints ps 0.5

#start printing summed route departures
set origin 0.0, 0.66
set ylabel '#departures'
stats file_summedDepartures u 1
set xrange [STATS_min:STATS_max]
plot file_summedDepartures using 1:2 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
file_summedDepartures using 1:3 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
file_summedDepartures using 1:4 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1

#start printing avg route tt's per departure time
set origin 0.0,0.33
set ylabel 'avg route tt [s]'
set xlabel 'time [s]'
plot file_avgRouteTTsByDepartureTime using 1:2 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
file_avgRouteTTsByDepartureTime using 1:3 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
file_avgRouteTTsByDepartureTime using 1:4 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1

#start printing starts per route
#set origin 0.0,0.0
#set xlabel 'time [s]'
#set ylabel '#starts'
#set yrange [0:1.5]
#plot file_routeStarts using 1:2 title "#starts on route 1" with impulses,\
#file_routeStarts using 1:3 title "#starts on route 2" with impulses,\
#file_routeStarts using 1:4 title "#starts on route 3" with impulses,\

#start printing avg route toll's per departure time
set origin 0.0,0.0
set ylabel 'avg route tolls'
set xlabel 'time [s]'
plot file_avgRouteTollsByDepartureTime using 1:2 notitle with points ls 1 pt 5 ps 0.5,\
1/0 title "upper route" with points ls 1 pt 5 ps 1,\
file_avgRouteTollsByDepartureTime using 1:3 notitle with points ls 2 pt 7 ps 0.5,\
1/0 title "middle route" with points ls 2 pt 7 ps 1,\
file_avgRouteTollsByDepartureTime using 1:4 notitle with points ls 3 pt 9 ps 0.5,\
1/0 title "lower route" with points ls 3 pt 9 ps 1

print "output written to:".output_file

#unset multiplot