FROM java:8

MAINTAINER delivery-engineering@netflix.com

COPY . workdir/

WORKDIR workdir

RUN ./gradlew buildDeb -x test

RUN dpkg -i ./front50-web/build/distributions/*.deb

CMD ["/opt/front50/bin/front50"]
